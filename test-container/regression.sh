echo -- Test 1 --
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

echo SUCCESS
echo -- summary of files --
echo Expected contains ...
ls -al ./test-expected/
echo Source contains ...
ls -al ./test-src/