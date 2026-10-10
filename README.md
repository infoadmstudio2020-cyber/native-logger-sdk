# NativeLoggerSDK

[![JitPack](https://jitpack.io/v/infoadmstudio2020-cyber/native-logger-sdk.svg)](https://jitpack.io/#infoadmstudio2020-cyber/native-logger-sdk)
[![License](https://img.shields.io/badge/License-Apache-2.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)

NativeLoggerSDK — Android Library

## Installation

Add JitPack to your root `build.gradle`:

```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency:

```gradle
dependencies {
    implementation 'com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1'
}
```

## Usage

```java
// Initialize the library
NativeLoggerSDK library = NativeLoggerSDK.getInstance(context);
library.init();
```

## Requirements

- Android API 21+
- Java 8+ / Kotlin 1.8+

## License

```
Copyright 2026 infoadmstudio2020-cyber

Licensed under the Apache-2.0 License.
```

See [LICENSE](LICENSE) for details.
