///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2014 Joliciel Informatique
//
//This file is part of Talismane.
//
//Talismane is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//Talismane is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with Talismane.  If not, see <http://www.gnu.org/licenses/>.
//////////////////////////////////////////////////////////////////////////////
package com.joliciel.talismane.lexicon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.NeedsTalismaneSession;
import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.utils.LogUtils;
import com.joliciel.talismane.utils.StringUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

/**
 * Used to deserialize a zip file containing an ordered set of lexicons
 * serialized by the {@link LexiconSerializer}.
 * 
 * @author Assaf Urieli
 *
 */
public class LexiconDeserializer {
	private static final Logger LOG = LoggerFactory.getLogger(LexiconDeserializer.class);

	private TalismaneSession talismaneSession;

	public static void main(String[] args) throws Exception {
		OptionParser parser = new OptionParser();
		parser.accepts("testLexicon", "test lexicon");

		OptionSpec<String> lexiconFilesOption = parser.accepts("lexicon", "lexicon(s), semi-colon delimited").withRequiredArg().ofType(String.class)
				.withValuesSeparatedBy(';');
		OptionSpec<String> wordsOption = parser.accepts("words", "comma-delimited list of words to test").withRequiredArg().required().ofType(String.class)
				.withValuesSeparatedBy(',');

		if (args.length <= 1) {
			parser.printHelpOn(System.out);
			return;
		}

		OptionSet options = parser.parse(args);

		Config config = null;
		if (options.has(lexiconFilesOption)) {
			List<String> lexiconFiles = options.valuesOf(lexiconFilesOption);

			Map<String, Object> values = new HashMap<>();
			values.put("talismane.core.lexicons", lexiconFiles);
			config = ConfigFactory.parseMap(values).withFallback(ConfigFactory.load());
		} else {
			config = ConfigFactory.load();
		}

		String sessionId = "";
		TalismaneSession talismaneSession = new TalismaneSession(config, sessionId);

		List<String> words = options.valuesOf(wordsOption);

		PosTaggerLexicon mergedLexicon = talismaneSession.getMergedLexicon();
		for (String word : words) {
			LOG.info("################");
			LOG.info("Word: " + word);
			List<LexicalEntry> entries = mergedLexicon.getEntries(word);
			for (LexicalEntry entry : entries) {
				LOG.info(entry + ", Full morph: " + entry.getMorphologyForCoNLL());
			}
		}
	}

	public LexiconDeserializer(TalismaneSession talismaneSession) {
		this.talismaneSession = talismaneSession;
	}

	public List<PosTaggerLexicon> deserializeLexicons(File lexiconFile) {
		if (!lexiconFile.exists())
			throw new TalismaneException("LexiconFile does not exist: " + lexiconFile.getPath());
		try {
			FileInputStream fis = new FileInputStream(lexiconFile);
			ZipInputStream zis = new ZipInputStream(fis);
			return this.deserializeLexicons(zis);
		} catch (IOException e) {
			LogUtils.logError(LOG, e);
			throw new RuntimeException(e);
		}
	}

	public List<PosTaggerLexicon> deserializeLexicons(ZipInputStream zis) {
		try {
			List<PosTaggerLexicon> lexicons = new ArrayList<PosTaggerLexicon>();
			Map<String, PosTaggerLexicon> lexiconMap = new HashMap<String, PosTaggerLexicon>();
			String[] lexiconNames = null;

			ZipEntry ze = null;
			while ((ze = zis.getNextEntry()) != null) {
				LOG.debug(ze.getName());
				if (ze.getName().endsWith(".obj")) {
					LOG.debug("deserializing " + ze.getName());
					ObjectInputStream in = new ObjectInputStream(zis);
					PosTaggerLexicon lexicon = (PosTaggerLexicon) in.readObject();
					lexiconMap.put(lexicon.getName(), lexicon);
				} else if (ze.getName().equals("lexicon.properties")) {
					Reader reader = new BufferedReader(new InputStreamReader(zis, "UTF-8"));
					Properties props = new Properties();
					props.load(reader);
					Map<String, String> properties = StringUtils.getArgMap(props);
					lexiconNames = properties.get("lexicons").split(",");
				}
			}

			for (String lexiconName : lexiconNames) {
				PosTaggerLexicon lexicon = lexiconMap.get(lexiconName);
				if (lexicon instanceof NeedsTalismaneSession)
					((NeedsTalismaneSession) lexicon).setTalismaneSession(talismaneSession);
				lexicons.add(lexicon);
			}

			return lexicons;
		} catch (IOException e) {
			LogUtils.logError(LOG, e);
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			LogUtils.logError(LOG, e);
			throw new RuntimeException(e);
		}
	}
}