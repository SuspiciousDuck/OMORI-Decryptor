# OMORI-Decryptor
Fork of standalone OMORI decryption program.
Modified to work with all copies of OMORI<br>
The original project can be found at [BenjaminUrquhart/OMORI-Decryptor](https://github.com/BenjaminUrquhart/OMORI-Decryptor)
### Obligatory reminder that you shouldn't pirate indie games and that you should support OMORI's developers
### Note: I haven't tested building on Windows or decrypting OMORI on versions older than v1.0.8
# Build instructions
## Linux
1. Install and set Java 8 as your default Java version. (Also install `git` if you haven't already) <br>
#### Archlinux example:
```
# pacman -Sy jre8-openjdk git --noconfirm --needed
# archlinux-java set java-8-openjdk
```
2. Clone this repository
```
$ git clone --depth=1 https://github.com/SuspiciousDuck/OMORI-Decryptor
$ cd OMORI-Decryptor
```
3. Compile using gradlew
```
$ chmod +x ./gradlew
$ ./gradlew shadowJar
```
4. Run output jar file
```
$ java -jar ./build/libs/OMORI-Decryptor-all.jar
```
## Windows
1. Install Java 8 on your Windows machine
2. Download this repository by clicking Code and Download ZIP
3. Extract the zip file
4. In powershell, enter the extracted folder and run `.\gradlew.bat shadowJar`
5. Your compiled Java file should be at `.\build\libs\OMORI-Decryptor-all.jar`