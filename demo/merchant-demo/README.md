# merchant-demo

Standalone Swing merchant demo. This project is isolated from `packgopay` backend code.

## Run

```bash
cd /Users/xiaoyou_03/IdeaProjects/PakGoPay/demo/merchant-demo
mvn -q -DskipTests package
java -jar target/merchant-demo.jar
```

## Package

```bash
# macOS: DMG + PKG
./scripts/package.sh --os mac --type all

# Windows: MSI + EXE (run on Windows host)
./scripts/package.sh --os windows --type all
```

> Note: `jpackage` requires JDK 17+ and platform-specific tooling.
> Build Windows installers on Windows, and macOS installers on macOS.
