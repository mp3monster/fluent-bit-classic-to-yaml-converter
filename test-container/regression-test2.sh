# set any environment configs for this test. Change the conversion.list as needed
export FLB_CONVERT_DEBUG=true

echo starting Test 2 ....
export FLB_PATH_PREFIX=./test-src/
java FLBConverter.java 
diff -B ./test-expected/test2.yaml ./test-src/test2.yaml >> ./test-expected/test2-diff.txt
diff -B ./test-expected/test3.yaml ./test-src/test3.yaml >> ./test-expected/test3-diff.txt

echo 2a ...
FILE=./test-expected/test2-diff.txt
if [[ ! -s $FILE ]] ; then
  echo Test 2a passed
else
  cat $FILE
  echo ============
  cat ./test-src/test2.yaml 
  echo ============
  echo Test 2a failed
  passed=false
fi

echo 2b ...
FILE=./test-expected/test3-diff.txt
if [[ ! -s $FILE ]] ; then
  echo Test 2b passed
else
  cat $FILE
  echo ============
  cat ./test-src/test3.yaml 
  echo ============  
  echo Test 2b failed
  passed=false
fi

echo ... Test 2 COMPLETED