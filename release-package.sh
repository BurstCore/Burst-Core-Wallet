#!/bin/sh
VERSION=$1
if [ -x ${VERSION} ];
then
	echo VERSION not defined
	exit 1
fi
APPLICATION="jrs"
PACKAGE=${APPLICATION}-client-${VERSION}
echo PACKAGE="${PACKAGE}"
CHANGELOG=nxt-client-${VERSION}.changelog.txt
OBFUSCATE=$2

FILES="changelogs conf html lib resource contrib"
FILES="${FILES} ${APPLICATION}.exe ${APPLICATION}service.exe"
FILES="${FILES} 3RD-PARTY-LICENSES.txt AUTHORS.txt LICENSE.txt"
FILES="${FILES} DEVELOPERS-GUIDE.md OPERATORS-GUIDE.md README.md README.txt USERS-GUIDE.md"
FILES="${FILES} mint.bat mint.sh run.bat run.sh run-tor.sh run-desktop.sh start.sh stop.sh compact.sh compact.bat sign.sh"
FILES="${FILES} nxt.policy nxtdesktop.policy Wallet.url Dockerfile"

unix2dos *.bat
echo compile
./compile.sh
echo updating constants.js
./constants-export.sh > /dev/null 2>&1
rm -rf html/doc/*
rm -rf ${APPLICATION}
rm -rf ${PACKAGE}.jar
rm -rf ${PACKAGE}.sh
rm -rf ${PACKAGE}.exe
rm -rf ${PACKAGE}.zip
mkdir -p ${APPLICATION}/
mkdir -p ${APPLICATION}/logs
mkdir -p ${APPLICATION}/addons/src

if [ "${OBFUSCATE}" = "obfuscate" ]; 
then
echo obfuscate
/opt/proguard/bin/proguard.sh @nxt.pro
mv ../nxt.map ../nxt.map.${VERSION}
else
FILES="${FILES} classes src COPYING.txt"
FILES="${FILES} compile.sh javadoc.sh jar.sh package.sh"
FILES="${FILES} win-compile.sh win-javadoc.sh win-package.sh"
echo javadoc
./javadoc.sh
fi
echo copy resources
cp installer/lib/JavaExe.exe ${APPLICATION}.exe
cp installer/lib/JavaExe.exe ${APPLICATION}service.exe
cp -a ${FILES} ${APPLICATION}
cp -a logs/placeholder.txt ${APPLICATION}/logs
echo gzip
for f in `find ${APPLICATION}/html -name *.gz`
do
	rm -f "$f"
done
for f in `find ${APPLICATION}/html -name *.html -o -name *.js -o -name *.css -o -name *.json  -o -name *.ttf -o -name *.svg -o -name *.otf`
do
	gzip -9c "$f" > "$f".gz
done
cd ${APPLICATION}
echo generate jar files
../jar.sh
echo package installer Jar
../installer/build-installer.sh ../${PACKAGE}
#echo create installer exe
#../installer/build-exe.bat ${PACKAGE}
echo create installer zip
cd -
zip -q -X -r ${PACKAGE}.zip ${APPLICATION} -x \*/.idea/\* \*/.gitignore \*/.git/\* \*/\*.log \*.iml ${APPLICATION}/conf/nxt.properties ${APPLICATION}/conf/logging.properties ${APPLICATION}/conf/localstorage/\*
rm -rf ${APPLICATION}

#echo creating full changelog
#echo "${PACKAGE}:" > changelog-full.txt
#echo >> changelog-full.txt
#cat changelogs/${CHANGELOG} >> changelog-full.txt
#echo >> changelog-full.txt
#echo "--------------------------------------------------------------------------------" >> changelog-full.txt
#cat changelogs/changelog.txt >> changelog-full.txt
#unix2dos changelog-full.txt

#echo signing zip package
#../jarsigner.sh ${PACKAGE}.zip

#echo signing jar package
#../jarsigner.sh ${PACKAGE}.jar

echo creating sh package
echo "#!/bin/sh\nexec java -jar \"\${0}\"\n\n" > ${PACKAGE}.sh
cat ${PACKAGE}.jar >> ${PACKAGE}.sh
chmod a+rx ${PACKAGE}.sh
rm -f ${PACKAGE}.jar

#echo creating change log ${CHANGELOG}
#echo "Release $1" > ${CHANGELOG}
#echo >> ${CHANGELOG}
#echo "https://bitbucket.org/JeanLucPicard/nxt/downloads/${PACKAGE}.zip" >> ${CHANGELOG}
#echo >> ${CHANGELOG}
#echo "sha256:" >> ${CHANGELOG}
#echo >> ${CHANGELOG}
#sha256sum ${PACKAGE}.zip >> ${CHANGELOG}

#echo >> ${CHANGELOG}
#echo "https://bitbucket.org/JeanLucPicard/nxt/downloads/${PACKAGE}.sh" >> ${CHANGELOG}
#echo >> ${CHANGELOG}
#echo "sha256:" >> ${CHANGELOG}
#echo >> ${CHANGELOG}
#sha256sum ${PACKAGE}.sh >> ${CHANGELOG}

#echo >> ${CHANGELOG}
#echo "https://bitbucket.org/JeanLucPicard/nxt/downloads/${PACKAGE}.exe" >> ${CHANGELOG}
#echo >> ${CHANGELOG}
##echo "sha256:" >> ${CHANGELOG}
##sha256sum ${PACKAGE}.exe >> ${CHANGELOG}
#echo "https://bitbucket.org/JeanLucPicard/nxt/downloads/nxt-installer-${VERSION}.dmg" >> ${CHANGELOG}
#echo >> ${CHANGELOG}

#echo "The exe, dmg, and sh packages must have a digital signature by \"Stichting NXT\"." >> ${CHANGELOG}

#if [ "${OBFUSCATE}" = "obfuscate" ];
#then
#echo >> ${CHANGELOG}
#echo >> ${CHANGELOG}
#echo "This is an experimental release for testing only. Source code is not provided." >> ${CHANGELOG}
#fi
#echo >> ${CHANGELOG}
#echo >> ${CHANGELOG}
#echo "Change log:" >> ${CHANGELOG}
#echo >> ${CHANGELOG}

#cat changelogs/${CHANGELOG} >> ${CHANGELOG}
#echo >> ${CHANGELOG}

gpg --detach-sign --armour --sign-with 0xC654D7FCFF18FD55 ${PACKAGE}.zip
gpg --detach-sign --armour --sign-with 0xC654D7FCFF18FD55 ${PACKAGE}.sh
##gpg --detach-sign --armour --sign-with 0xC654D7FCFF18FD55 ${PACKAGE}.exe

#gpg --clearsign --sign-with 0xC654D7FCFF18FD55 ${CHANGELOG}
#rm -f ${CHANGELOG}
gpgv ${PACKAGE}.zip.asc ${PACKAGE}.zip
gpgv ${PACKAGE}.sh.asc ${PACKAGE}.sh
##gpgv ${PACKAGE}.exe.asc ${PACKAGE}.exe
#gpgv ${CHANGELOG}.asc
#sha256sum -c ${CHANGELOG}.asc
##jarsigner -verify ${PACKAGE}.zip
#jarsigner -verify ${PACKAGE}.sh


