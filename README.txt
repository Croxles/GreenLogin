GreenLogin 1.0.0
Target: Paper 26.1.2 build 74, Java 25

Commands:
  /kayit <şifre> <şifre>  - first-time registration
  /gir <şifre>            - log in

Features:
- Three failed login attempts, then kick.
- Green title text with remaining attempts.
- Unauthenticated players cannot move, chat, use most commands, interact, break/place blocks, or open/use inventory.
- Passwords are stored as salted PBKDF2 hashes in plugins/GreenLogin/users.yml.
- OPs / users with greenlogin.admin permission bypass login.

Build:
1. Install JDK 25 and Maven on a computer.
2. Open this folder in a terminal.
3. Run: mvn package
4. Copy target/GreenLogin-1.0.0.jar into the server's plugins folder.
5. Restart the server.

Important:
- Back up your server before installing plugins.
- This ZIP contains source code, not a precompiled plugin JAR. Build it with Maven to create the JAR.
- Passwords entered as command arguments may appear in command logs on some server setups. For a public server, consider a login plugin with chat-input password handling or disable command logging where appropriate.
- The plugin is configured for Paper 26.1.2 build 74 and Java 25. If your exact server build differs, compile/test against that Paper API.
