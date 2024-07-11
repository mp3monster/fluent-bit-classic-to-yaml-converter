copy ..\source\FLBConverter\*.java .
docker build . --no-cache --build-arg="RELEASE=main" -t flb-converter-regression
del *.java