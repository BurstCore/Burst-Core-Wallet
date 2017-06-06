REM Edit Line 1 to point to your JDK Bin Directory

set path=%path%;D:\Program Files\Java\jdk1.8.0_121\bin
set CP="conf/;classes/;lib/*"
set SP=src/java/

mkdir classes

dir src\*.java /s /B > sources.txt 
javac -encoding utf8 -sourcepath %SP% -classpath %CP% -d classes/ @sources.txt

del /f burst.jar 
jar cf burst.jar -C classes . 
rmdir /s /q classes
