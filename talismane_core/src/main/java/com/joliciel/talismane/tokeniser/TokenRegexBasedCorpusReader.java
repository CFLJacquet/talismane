package com.joliciel.talismane.tokeniser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.Annotator;
import com.joliciel.talismane.LinguisticRules;
import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.filters.Sentence;
import com.joliciel.talismane.sentenceDetector.SentenceDetectorAnnotatedCorpusReader;
import com.joliciel.talismane.sentenceDetector.SentencePerLineCorpusReader;
import com.joliciel.talismane.tokeniser.filters.TokenSequenceFilter;
import com.joliciel.talismane.utils.ConfigUtils;
import com.typesafe.config.Config;

/**
 * A corpus reader that expects one token per line, and analyses the line
 * content based on a regex.<br/>
 * The regex needs to contain a capturing group indicated by the following
 * strings:<br/>
 * <li>%TOKEN%: the token - note that we assume CoNLL formatting (with
 * underscores for spaces and for empty tokens). The sequence &amp;und; should
 * be used for true underscores.</li> It can optionally contain the following
 * capturing groups as well:<br/>
 * <li>%FILENAME%: the file containing the token</li>
 * <li>%ROW%: the row containing the token</li>
 * <li>%COLUMN%: the column containing the token</li> The token placeholder will
 * be replaced by (.*). Other placeholders will be replaced by (.+) meaning no
 * empty strings allowed.
 * 
 * @author Assaf Urieli
 *
 */
public class TokenRegexBasedCorpusReader extends TokeniserAnnotatedCorpusReader {
	private static final Logger LOG = LoggerFactory.getLogger(TokenRegexBasedCorpusReader.class);

	private static final String TOKEN_PLACEHOLDER = "%TOKEN%";
	private static final String FILENAME_PLACEHOLDER = "%FILENAME%";
	private static final String ROW_PLACEHOLDER = "%ROW%";
	private static final String COLUMN_PLACEHOLDER = "%COLUMN%";
	private Map<String, Integer> placeholderIndexMap = new HashMap<String, Integer>();
	private PretokenisedSequence tokenSequence = null;

	private int lineNumber = 0;
	private int sentenceCount = 0;

	private SentenceDetectorAnnotatedCorpusReader sentenceReader = null;

	private final String regex;
	private final Scanner scanner;
	private final TalismaneSession talismaneSession;
	private final Pattern pattern;
	private final TalismaneSession session;

	public TokenRegexBasedCorpusReader(Reader reader, Config config, TalismaneSession session) throws IOException {
		super(reader, config, session);
		this.session = session;
		this.regex = config.getString("preannotated-pattern");
		this.talismaneSession = session;
		this.scanner = new Scanner(reader);

		String configPath = "sentence-file";
		if (config.hasPath(configPath)) {
			InputStream sentenceReaderFile = ConfigUtils.getFileFromConfig(config, configPath);
			Reader sentenceFileReader = new BufferedReader(new InputStreamReader(sentenceReaderFile, session.getInputCharset()));
			SentenceDetectorAnnotatedCorpusReader sentenceReader = new SentencePerLineCorpusReader(sentenceFileReader, config, session);
			this.sentenceReader = sentenceReader;
		}

		// construct a pattern based on the regex
		int tokenPos = regex.indexOf(TOKEN_PLACEHOLDER);
		if (tokenPos < 0)
			throw new TalismaneException("The regex must contain the string \"" + TOKEN_PLACEHOLDER + "\"");

		int filenamePos = regex.indexOf(FILENAME_PLACEHOLDER);
		int rowNumberPos = regex.indexOf(ROW_PLACEHOLDER);
		int columnNumberPos = regex.indexOf(COLUMN_PLACEHOLDER);
		Map<Integer, String> placeholderMap = new TreeMap<Integer, String>();
		placeholderMap.put(tokenPos, TOKEN_PLACEHOLDER);

		if (filenamePos >= 0)
			placeholderMap.put(filenamePos, FILENAME_PLACEHOLDER);
		if (rowNumberPos >= 0)
			placeholderMap.put(rowNumberPos, ROW_PLACEHOLDER);
		if (columnNumberPos >= 0)
			placeholderMap.put(columnNumberPos, COLUMN_PLACEHOLDER);

		for (int j = 0; j < regex.length(); j++) {
			if (regex.charAt(j) == '(') {
				placeholderMap.put(j, "");
			}
		}
		int i = 1;
		for (int placeholderIndex : placeholderMap.keySet()) {
			String placeholderName = placeholderMap.get(placeholderIndex);
			if (placeholderName.length() > 0)
				placeholderIndexMap.put(placeholderName, i);
			i++;
		}

		String regexWithGroups = regex.replace(TOKEN_PLACEHOLDER, "(.*?)");
		regexWithGroups = regexWithGroups.replace(FILENAME_PLACEHOLDER, "(.+?)");
		regexWithGroups = regexWithGroups.replace(ROW_PLACEHOLDER, "(.+?)");
		regexWithGroups = regexWithGroups.replace(COLUMN_PLACEHOLDER, "(.+?)");

		this.pattern = Pattern.compile(regexWithGroups, Pattern.UNICODE_CHARACTER_CLASS);
	}

	@Override
	public boolean hasNextTokenSequence() {
		if (this.getMaxSentenceCount() > 0 && sentenceCount >= this.getMaxSentenceCount()) {
			// we've reached the end, do nothing
		} else {
			while (tokenSequence == null) {
				List<TokenTuple> tuples = new ArrayList<>();
				if (!scanner.hasNextLine())
					break;
				while (scanner.hasNextLine() || tuples.size() > 0) {
					String line = "";
					if (scanner.hasNextLine())
						line = scanner.nextLine().replace("\r", "");
					lineNumber++;
					if (line.length() > 0) {

						Matcher matcher = this.getPattern().matcher(line);
						if (!matcher.matches())
							throw new TalismaneException("Didn't match pattern \"" + regex + "\" on line " + lineNumber + ": " + line);

						if (matcher.groupCount() < placeholderIndexMap.size()) {
							throw new TalismaneException("Expected at least " + placeholderIndexMap.size() + " matches (but found " + matcher.groupCount()
									+ ") on line " + lineNumber);
						}

						String word = matcher.group(placeholderIndexMap.get(TOKEN_PLACEHOLDER));
						word = talismaneSession.getCoNLLFormatter().fromCoNLL(word);

						TokenTuple tuple = new TokenTuple(word);

						if (placeholderIndexMap.containsKey(FILENAME_PLACEHOLDER))
							tuple.fileName = (matcher.group(placeholderIndexMap.get(FILENAME_PLACEHOLDER)));
						if (placeholderIndexMap.containsKey(ROW_PLACEHOLDER))
							tuple.lineNumber = (Integer.parseInt(matcher.group(placeholderIndexMap.get(ROW_PLACEHOLDER))));
						if (placeholderIndexMap.containsKey(COLUMN_PLACEHOLDER))
							tuple.columnNumber = (Integer.parseInt(matcher.group(placeholderIndexMap.get(COLUMN_PLACEHOLDER))));
						tuples.add(tuple);
					} else {
						if (tuples.size() == 0)
							continue;

						// end of sentence

						boolean includeMe = true;

						// check cross-validation
						if (this.getCrossValidationSize() > 0) {
							if (this.getIncludeIndex() >= 0) {
								if (sentenceCount % this.getCrossValidationSize() != this.getIncludeIndex()) {
									includeMe = false;
								}
							} else if (this.getExcludeIndex() >= 0) {
								if (sentenceCount % this.getCrossValidationSize() == this.getExcludeIndex()) {
									includeMe = false;
								}
							}
						}

						if (this.getStartSentence() > sentenceCount) {
							includeMe = false;
						}

						sentenceCount++;
						LOG.debug("sentenceCount: " + sentenceCount);

						if (!includeMe) {
							tuples = new ArrayList<>();
							tokenSequence = null;
							continue;
						}

						Sentence sentence = null;
						if (sentenceReader != null && sentenceReader.hasNextSentence()) {
							sentence = sentenceReader.nextSentence();
						} else {
							LinguisticRules rules = talismaneSession.getLinguisticRules();
							if (rules == null)
								throw new TalismaneException("Linguistic rules have not been set.");

							String text = "";
							for (TokenTuple tuple : tuples) {
								String word = tuple.word;
								// check if a space should be added before this
								// token

								if (rules.shouldAddSpace(text, word))
									text += " ";
								text += word;
							}
							sentence = new Sentence(text, talismaneSession);
						}

						for (Annotator annotator : session.getTextAnnotators()) {
							annotator.annotate(sentence);
						}

						tokenSequence = new PretokenisedSequence(sentence, talismaneSession);
						for (TokenTuple tuple : tuples) {
							Token token = tokenSequence.addToken(tuple.word);
							token.setFileName(tuple.fileName);
							token.setLineNumber(tuple.lineNumber);
							token.setColumnNumber(tuple.columnNumber);
						}
						tokenSequence.cleanSlate();

						if (LOG.isTraceEnabled()) {
							LOG.trace("Next sentence: " + sentence.getText().toString());
							LOG.trace("Tokenised: " + tokenSequence.toString());
						}

						// now apply the token sequence filters
						for (TokenSequenceFilter tokenFilter : session.getTokenSequenceFilters()) {
							tokenFilter.apply(tokenSequence);
						}

						break;
					}
				}
			}
		}
		return (tokenSequence != null);
	}

	private static final class TokenTuple {
		public TokenTuple(String word) {
			this.word = word;
		}

		public String word;
		public String fileName;
		public int lineNumber = -1;
		public int columnNumber = -1;

		@Override
		public String toString() {
			return "TokenTuple [word=" + word + ", fileName=" + fileName + ", lineNumber=" + lineNumber + ", columnNumber=" + columnNumber + "]";
		}
	}

	@Override
	public TokenSequence nextTokenSequence() {
		TokenSequence nextSequence = tokenSequence;
		tokenSequence = null;
		return nextSequence;
	}

	/**
	 * The regex used to find the tokens.
	 */
	public String getRegex() {
		return regex;
	}

	public Pattern getPattern() {
		return pattern;
	}

	@Override
	public Map<String, String> getCharacteristics() {
		return super.getCharacteristics();
	}

	/**
	 * If provided, will assign sentences with the original white space to the
	 * token sequences.
	 */
	public SentenceDetectorAnnotatedCorpusReader getSentenceReader() {
		return sentenceReader;
	}

	public void setSentenceReader(SentenceDetectorAnnotatedCorpusReader sentenceReader) {
		this.sentenceReader = sentenceReader;
	}

	@Override
	public boolean hasNextSentence() {
		return this.hasNextTokenSequence();
	}

	@Override
	public Sentence nextSentence() {
		return this.nextTokenSequence().getSentence();
	}

	@Override
	public boolean isNewParagraph() {
		return false;
	}
}