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

package org.platanios.symphony.mt

import org.platanios.tensorflow.api._

/**
  * @author Emmanouil Antonios Platanios
  */
package object models {
  type TFSentencesDataset = tf.data.Dataset[
      (Tensor, Tensor), (Output, Output),
      (DataType, DataType), (Shape, Shape)]

  type TFSentencePairsDataset = tf.data.Dataset[
      ((Tensor, Tensor), (Tensor, Tensor)),
      ((Output, Output), (Output, Output)),
      ((DataType, DataType), (DataType, DataType)),
      ((Shape, Shape), (Shape, Shape))]

  type TFInputDataset = tf.data.Dataset[
      (Tensor, Tensor, Tensor, Tensor), (Output, Output, Output, Output),
      (DataType, DataType, DataType, DataType), (Shape, Shape, Shape, Shape)]

  type TFTrainDataset = tf.data.Dataset[
      ((Tensor, Tensor, Tensor, Tensor), (Tensor, Tensor)),
      ((Output, Output, Output, Output), (Output, Output)),
      ((DataType, DataType, DataType, DataType), (DataType, DataType)),
      ((Shape, Shape, Shape, Shape), (Shape, Shape))]
}