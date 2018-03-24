#!/bin/bash

cd -P -- "$(dirname -- "$0")/.."
sbt "mt/runMain org.platanios.symphony.mt.experiments.Experiment
  --task train
  --working-dir temp/experiments
  --data-dir temp/data
  --dataset iwslt15
  --language-pairs en:cs,en:de,en:fr,en:th,en:vi,en:zh
  --eval-datasets tst2013
  --eval-metrics bleu,hyp_len,ref_len,sen_cnt
  --tokenizer moses
  --cleaner moses
  --vocabulary generated:20000
  --batch-size 128
  --num-buckets 5
  --src-max-length 50
  --tgt-max-length 50
  --buffer-size 1024
  --model-arch bi_rnn:2:2
  --model-cell lstm:tanh
  --model-type pairwise
  --word-embed-size 512
  --lang-embed-size 8
  --residual
  --attention
  --dropout 0.2
  --label-smoothing 0.1
  --beam-width 10
  --length-penalty 0.6
  --opt amsgrad:0.001
  --opt-max-norm 100.0
  --num-steps 100000
  --summary-steps 100
  --checkpoint-steps 1000
  --log-loss-steps 100
  --log-eval-steps 5000
  --launch-tensorboard
  --tensorboard-host localhost
  --tensorboard-port 6006
  --num-gpus 1
  --seed 10"
