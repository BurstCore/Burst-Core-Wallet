REM Edit Line 1 to point to your JDK Bin Directory

set path=%path%;D:\Program Files\Java\jdk1.8.0_121\bin
set CP="conf/;classes/;lib/*"
set SP=src/java/

rmdir /s /q html\doc
mkdir html\doc

javadoc -quiet -sourcepath %SP% -classpath %CP% -protected -splitindex -subpackages nxt -d html/doc/


