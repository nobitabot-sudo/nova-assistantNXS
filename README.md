# Nova

Nova is a personal, J.A.R.V.I.S.-style AI assistant for Android. It listens, talks, reads the screen
and operates the phone for you (open apps, tap, type, scroll) through Android's Accessibility Service.

This is a personal-use fork of the open-source "Panda" / "blurr" app (Personal Use License, see `LICENSE`),
with these changes:

- **No login, no accounts, no cloud.** Firebase, Google sign-in, billing and analytics are removed.
  Memories and task history are stored on the phone only.
- **Bring your own AI.** Open the app, go to the **AI** tab and enter any OpenAI-compatible
  `Base URL`, `API key` and `Model` (OpenRouter, Groq, a free multi-provider gateway, a local server, ...).
  Several keys can be separated by commas and are used in turn.
- **Jarvis persona** and an offline **"Jarvis" / "Nova" wake word** (Vosk, no account or key needed).
- Uses the phone's own text-to-speech voice, no Google Cloud key needed.

Licence reminder: the Personal Use License allows personal, educational and non-commercial use only.

## Get the APK

1. Put this folder in a new **private** GitHub repository (see below).
2. GitHub Actions builds the app automatically (Actions tab, workflow "Build Nova APK").
3. Download `Nova.apk` from the repository's **Releases** page (tag `latest`) on your phone and install it.

```
git init
git add .
git commit -m "Nova"
git branch -M main
git remote add origin https://github.com/<your-username>/nova.git
git push -u origin main
```

If the build fails, open the failed run, copy the red error lines and send them to your assistant.

## First run on the phone

1. Install the APK and open Nova.
2. Enter the AI details on the **AI Settings** screen and press *Save and test connection*.
3. Go through the permission steps: Accessibility, Display over other apps, Microphone,
   Notifications, and turn off battery optimization for Nova so it can keep listening.
4. Optional: in the Settings tab press "Enable wake word", then say "Jarvis" or "Nova".

Local builds in Android Studio: the wake word model is downloaded by the GitHub workflow. To build locally, put the
unzipped `vosk-model-small-en-in-0.4` folder at `app/src/main/assets/vosk-model/` and add a text file named `uuid` inside it.
