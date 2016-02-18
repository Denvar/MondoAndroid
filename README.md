# MondoAndroid

An Android app for [Mondo](https://getmondo.co.uk/)

## Configuration
If you have developer access to the Mondo API you can create a non-confidental auth client in the developer console. 
Then, create a config file with you auth client details that looks like this:
```
public class Config {
    public static final String BASE_URL = "https://api.getmondo.co.uk";
    public static final String ACCOUNT_ID = "your account id";
    public static final String CLIENT_ID = "your client id";
    public static final String CLIENT_SECRET = "your client secret";
}
```
Put that config file in the package `tech.jonas.mondoandroid.api` and you're good to go.

## Build Instructions
Just use the gradle wrapper to build the app: `$ ./gradlew assembleDebug`
