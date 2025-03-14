# Code Signing

Code signing is a way for a computer to verify that the software it runs comes from a trusted source and hasn’t been altered.

Official releases of Processing are signed by our automated build process. If you only ever want to download and use Processing, you should not have to worry about code signing.

However, if you want to share your own custom builds of Processing with someone else, you might need to sign the code with your own certificates.

## macOS

Code signing is especially important for macOS, as Apple blocks unsigned software from running on their system. If you build Processing locally and share the executable with someone else, you will need to sign your code.

### Pre-requisites
1. Access to a computer running macOS (Apple only allows macOS certificates to be created on a Mac)
2. An Apple Developer account (https://developer.apple.com) 
3. A recent version of Xcode
5. The "Account Holder" role on your Apple Developer team (needed for setting up the GitHub action) 

### macOS Code Signing with GitHub Actions

We need to sign the `Processing.app` executable itself, as well as the included Java Runtime Environment (JRE).

For this we will use the [import-codesign-certs](https://github.com/Apple-Actions/import-codesign-certs) GitHub Action.

#### Create Your Local Signing Certificates

Open Xcode and log in with your Apple Developer account if you aren't already.

Still in Xcode, navigate to **Preferences**. Go to the **Accounts** tab, select your user account, and click **Manage Certificates**. Then, click the **+** button and select **macOS Developer ID**.

#### Create a `.p12` file
A `.p12` file contains your signing certificates, which will be used to prove that the app comes from you.

To create your `.p12` file, follow [this tutorial](https://calvium.com/how-to-make-a-p12-file/) with the following step altered:

In the **create new certificate** section, do NOT select `iOS distribution` but instead select `Developer ID Application`. This allows the certificate to be used to distribute an app outside of the Mac App Store.

Make sure to create a strong password when prompted. If possible, save the password in a password manager. You will need it in the next step.

#### Upload your Signing Certificates to GitHub

In a terminal window, navigate to the location of the `.p12` file you generated in the previous step.

Copy the file to your clipboard using the following command:
```bash
$ base64 -i CertificateFile.p12 | pbcopy
```
In your Github Repository, create a secret called `CERTIFICATES_P12` and paste the content of your clipboard into it.

Create another secret called `CERTIFICATES_P12_PASSWORD` and save your `.p12` password into it.

The GitHub Action should now be set up to sign Processing and the JRE.

### Notarisation
Notarization is a security process required by Apple for macOS software. It means that the software has been checked by Apple to ensure it doesn’t contain malicious code. This is an additional step, different from code signing.

When a macOS application is notarized, Apple reviews it and issues a “stamp of approval.” This lets macOS users run the software without warnings or blocks. Without notarization, macOS might prevent the software from opening or display a security warning, even if the software is safe.

To prevent the *this application is not recognised by Mac OS* error we will setup the Github Action to submit the release to Apple for Notarisation.

Go to https://developer.apple.com/account to find the team ID in the membership details section and save that ID to the `PROCESSING_TEAM_ID` secret.

Enter an Apple ID email in the `PROCESSING_APPLE_ID` secret, this account does **not** need to have an Account Holder role, ideally it is not anyone's personal Apple ID, as the password to the Apple ID will be included into the repositories secrets.

Follow these instructions https://support.apple.com/en-us/102654 to setup an app-specific password for the Apple ID and enter that as `PROCESSING_APP_PASSWORD`

### Release

Finally create a GitHub release to test the signing and notarization actions. 

### Sign locally

Alternatively, or if you don't want to use the Github Action, you can sign and notarize your build of Processing locally using `ant macos-dist `

#### Pre-requisites
- Use a computer running macOS
- Have an Apple Developer account
- Make sure you are logged into your Apple ID
- Setup and Log into XCode

#### Code Sign with `ant macos-dist `

In a terminal window, `cd` into the repository then run the following command:

```bash
$ PROCESSING_TEAM_ID=... PROCESSING_APPLE_ID=... PROCESSING_APP_PASSWORD=... ant macos-dist 
```

Where `...` are replaced with your credentials.
