# Processing 32-bit Linux

For the Raspberry Pi 3 and older we'd need to create an 32-bit arm version of
processing.

Github Actions do not support creating 32 bit runners so we need to host one
ourselves.

## Instructions

### Gathering your Tools

You will need:

- A Raspberry Pi
- A micro SD card
- An SD card reader
- A computer

### Flashing the SD Card
1. Install and open [**Raspberry Pi Imager**](https://www.raspberrypi.com/software/) on your computer
2. In **Raspberry Pi Imager**:
  - Select your Raspberry Pi model
  - Select "Raspberry PI OS lite (32bit)" as the OS (you may need to look into the sub-menus)
  - Select your SD card
  - Click NEXT
3. Edit the OS settings:
  - Set a hostname (e.g. `processing.local`)
  - Set a username and password
  - Go to the SERVICES tab and enable SSH with password authentication
  - Click SAVE and Apply the OS customisation settings

### Configuring the Raspberry Pi
1. Take the written SD card and put it into the Raspberry PI
2. Power it up and wait for it to boot
3. SSH into the Raspberry Pi using the hostname, username, and password you set earlier
4. Follow Github's [instructions on how to set up a self-hosted runner](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/adding-self-hosted-runners). _Note: In the `./config.sh` setup step, you will be prompted to enter a name and other settings. Use default settings by pressing ENTER for each prompt._
5. Verify that the new runner appears in the list of runners on GitHub.
6. Quit the the runner in the terminal (we will set it up to run automatically on boot).
7. Use Github [instructions to setup the runner as a services](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/configuring-the-self-hosted-runner-application-as-a-service) so it runs on boot.

Done.
