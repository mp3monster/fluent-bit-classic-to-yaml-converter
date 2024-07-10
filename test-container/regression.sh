echo -- Test 1 --
# set any environment configs for this test. Change the conversion.list as needed
java FLBConverter.java ./test-src/test.conf
diff -B ./test-expected/test.yaml ./test-src/test.yaml >> ./test-expected/test1-diff.txt

FILE=./test-expected/test1-diff.txt

if [[ ! -s $FILE ]] ; then
  echo Test 1 passed
else
  cat $FILE
  echo Test 1 failed
  exit 1
fi

# ---- end of test 1 ----

echo -- Test 2 --
# set any environment configs for this test. Change the conversion.list as needed
export FLB_PATH_PREFIX=./test-src/
java FLBConverter.java 
diff -B ./test-expected/test2.yaml ./test-src/test2.yaml >> ./test-expected/test2-diff.txt
diff -B ./test-expected/test3.yaml ./test-src/test3.yaml >> ./test-expected/test3-diff.txt

FILE=./test-expected/test2-diff.txt

if [[ ! -s $FILE ]] ; then
  echo Test 1 passed
else
  cat $FILE
  echo Test 1 failed
  exit 1
fi

FILE=./test-expected/test3-diff.txt

if [[ ! -s $FILE ]] ; then
  echo Test 1 passed
else
  cat $FILE
  echo Test 1 failed
  exit 1
fi
# ---- end of test 2 ----

echo -- Test 3 --
# set any environment configs for this test. Change the conversion.list as needed
export FLB_IDIOMATICFORM=true
java FLBConverter.java ./test-src/test4.conf
diff -B ./test-expected/test.yaml ./test-src/test.yaml >> ./test-expected/test1-diff.txt

FILE=./test-expected/test1-diff.txt

if [[ ! -s $FILE ]] ; then
  echo Test 1 passed
else
  cat $FILE
  echo Test 1 failed
  exit 1
fi

echo
echo
echo SUCCESS
echo
echo -- summary of files --
echo Expected contains ...
ls -al ./test-expected/
echo Source contains ...
ls -al ./test-src/