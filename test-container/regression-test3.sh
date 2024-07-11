echo starting Test 3 ....
# set any environment configs for this test. Change the conversion.list as needed
export FLB_IDIOMATICFORM=true
java FLBConverter.java test4.conf
diff -B ./test-expected/test4.yaml ./test-src/test4.yaml >> ./test-expected/test4-diff.txt

FILE=./test-expected/test4-diff.txt

if [[ ! -s $FILE ]] ; then
  echo Test 3 passed
else
  cat $FILE
  echo Test 3 failed
  passed=false
fi

echo ... Test 3 COMPLETED