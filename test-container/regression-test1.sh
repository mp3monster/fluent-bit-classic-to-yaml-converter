# set any environment configs for this test. Change the conversion.list as needed
echo starting Test 1 ....
java FLBConverter.java ./test-src/test.conf
diff -B ./test-expected/test.yaml ./test-src/test.yaml >> ./test-expected/test1-diff.txt

FILE=./test-expected/test1-diff.txt

if [[ ! -s $FILE ]] ; then
  echo Test 1 passed
  passed=true
else
  cat $FILE
  echo Test 1 failed
  passed=false
fi

echo ... Test 1 COMPLETED