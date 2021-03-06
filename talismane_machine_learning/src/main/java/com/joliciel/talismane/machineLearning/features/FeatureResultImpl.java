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
package com.joliciel.talismane.machineLearning.features;

import java.util.List;

final class FeatureResultImpl<T> implements FeatureResult<T> {
  private Feature<?, T> feature;
  private T outcome;
  private String trainingName = null;

  public FeatureResultImpl(Feature<?, T> feature, T outcome) {
    this.feature = feature;
    if (outcome == null)
      throw new RuntimeException("Trying to set null outcome");

    this.outcome = outcome;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.joliciel.talismane.posTagger.features.FeatureResult#getFeature()
   */
  @Override
  public Feature<?, T> getFeature() {
    return feature;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.joliciel.talismane.posTagger.features.FeatureResult#getOutcome()
   */
  @Override
  public T getOutcome() {
    return this.outcome;
  }

  @Override
  public String toString() {
    String string = this.getTrainingName();
    if (outcome instanceof Double || outcome instanceof Integer || outcome instanceof List)
      string += "=" + outcome.toString();
    return string;
  }

  @Override
  public String getTrainingName() {
    if (trainingName == null) {
      trainingName = feature.getName();
      if (!(outcome instanceof Double || outcome instanceof Integer || outcome instanceof List)) {
        String string = null;
        if (outcome instanceof String) {
          string = this.getTrainingOutcome((String) outcome);
        } else {
          string = outcome.toString();
        }
        trainingName += ":" + string;
      }
    }
    return trainingName;
  }

  public String getTrainingOutcome(String outcome) {
    String string = outcome;
    string = string.replace(' ', '·');
    string = string.replace('=', '≈');
    string = string.replace('\n', '¬');
    return string;
  }
}
