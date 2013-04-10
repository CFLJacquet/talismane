///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2013 Assaf Urieli
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
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with Talismane. If not, see <http://www.gnu.org/licenses/>.
//////////////////////////////////////////////////////////////////////////////
package com.joliciel.talismane.parser.features;

import com.joliciel.talismane.machineLearning.features.AbstractFeature;
import com.joliciel.talismane.machineLearning.features.Feature;
import com.joliciel.talismane.machineLearning.features.FeatureResult;
import com.joliciel.talismane.machineLearning.features.RuntimeEnvironment;
import com.joliciel.talismane.posTagger.features.PosTaggedTokenAddressFunction;
import com.joliciel.talismane.posTagger.features.PosTaggedTokenWrapper;

/**
 * Simply returns the PosTaggedTokenWrapper that was passed in in the first place.
 * @author Assaf Urieli
 *
 */
public class ImplicitAddressFeature extends AbstractFeature<PosTaggedTokenWrapper,PosTaggedTokenWrapper> implements PosTaggedTokenAddressFunction<PosTaggedTokenWrapper> {
	@Override
	public FeatureResult<PosTaggedTokenWrapper> check(
			PosTaggedTokenWrapper context, RuntimeEnvironment env) {

		FeatureResult<PosTaggedTokenWrapper> result = this.generateResult(context);
		return result;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Class<? extends Feature> getFeatureType() {
		return PosTaggedTokenAddressFunction.class;
	}

}