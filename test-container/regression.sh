passed=true
# -- Test 1 --
source regression-test1.sh

# ---- end of test 1 ----

# -- Test 2 --
if $passed; then
  source regression-test2.sh
fi
# ---- end of test 2 ----

# -- Test 3 --
if $passed; then
  source regression-test3.sh
fi
# ---- end of test 3 ----

echo
echo -- summary of files --
echo Expected contains ...
ls -al ./test-expected/
echo 
echo Source contains ...
ls -al ./test-src/
echo
echo ------
echo
echo Completed - passed == $passed
echo
