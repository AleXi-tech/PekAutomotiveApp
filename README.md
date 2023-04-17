# Android Network Communication App

This Android app allows users to establish a network connection and send messages to a server. The app uses Kotlin, Android Jetpack components, and follows the Model-View-ViewModel (MVVM) architectural pattern. The server app is written in Python Flask and can be found in one of my other repositories.

[Server App Repository with Python Flask](https://github.com/AleXi-tech/PekAutoFlask)

## Features

- Connect to a server via IP address
- Send messages to the server
- Show connection status
- Option to manually enter the server's IP address
- Reset connection

## Technical Details

### Libraries and Frameworks
- Kotlin programming language
- Android Jetpack components (ViewModel, LiveData, Data Binding, and Lifecycle components)
- AndroidX libraries
- Material Components for Android
- Coroutine for asynchronous tasks
- Java Sockets for network communication

### Architectural Pattern
- Model-View-ViewModel (MVVM)

## App Structure

The app consists of the following main components:

- `Commons` class: A utility class with a companion object that contains a function `intToIp` which converts an integer to an IP address.
- `Constants` object: A singleton object that stores constants such as the connection timeout, port, and socket timeout.
- `MainViewModel` class: A ViewModel class that extends `ViewModel` and contains LiveData objects to manage and store the application's UI data. It also contains a few variables for user input.
- `MainActivity` class: An `AppCompatActivity` that handles the UI interactions and works with the `MainViewModel`. It sets up views, listeners, and shared preferences, and manages the network connection using a `NetworkManager` object.
- `NetworkManager` class: A custom class responsible for managing the network connection, connecting to the server, and sending messages using Java Sockets.

## Getting Started

1. Clone the repository
2. Open the project in Android Studio
3. Build and run the app on an emulator or a physical device
4. The app will display a connection warning dialog on the first run. Press "OK" to proceed.
5. The app will attempt to establish a connection to the server. If successful, you can send messages to the server using the "Send" button.
6. You can manually enter the server's IP address by checking the "Manual IP" checkbox.
7. To reset the connection, click on the menu icon in the top right corner and select "Reset Connection".

**Note**: Ensure that the server is running and accessible when using the app. Find the Python Flask server app in one of my other repositories: [Server App Repository with Python Flask](https://github.com/AleXi-tech/PekAutoFlask)
