/* Copyright 2017-18, Emmanouil Antonios Platanios. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.platanios.symphony.mt.data.statistics

import org.platanios.symphony.mt.Language

import scala.collection.mutable.ArrayBuffer

/**
  * @author Emmanouil Antonios Platanios
  */
class SentenceLengthsHistogram(
    val minLength: Int,
    val maxLength: Int
) extends SentenceStatistic[Seq[Int]] {
  override def forSentences(language: Language, sentences: Seq[Seq[String]]): Seq[Int] = {
    val zeros = ArrayBuffer.fill(maxLength - minLength + 1)(0)
    sentences.map(sentence => {
      math.max(minLength, math.min(maxLength, sentence.length)) - minLength
    }).foreach(l => zeros(l) += 1)
    zeros
  }

  override def aggregate(values: Seq[Seq[Int]]): Seq[Int] = {
    values.transpose.map(_.sum)
  }
}