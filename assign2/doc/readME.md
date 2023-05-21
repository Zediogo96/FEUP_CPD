# Distributed Systems Assignment 22 / 23

## Compilation

Navigate to the /src folder.

### - Server

In order to compile and execute the server, you need to run the following commands:

```bash
fuser -k 1234/tcp; # kill the process running on port 1234
javac main/Server.java; # compile the server code
java main.Server (unranked || ranked) MAX_N_GAMES PLAYERS_PER_GAME N_ROUNDS_PER_GAME #execute the server

# an example of the last command is:
java main.Server ranked 2 3 4
```

### - Client

In order to compile and execute the client, you need to run the following commands:

```bash
javac main/Client.java # compile the client code
java main/Client (login || register) username password # execute the client

# The possible usages of the last command are:
java main.Client register zediogo apple1
java main.Client login zediogo apple1 
```

## Usage

In order to facilitate the testing of our project, we've prepared two scripts that will run the server and the client in different terminals automatically, according to some of the use / testing cases.

These scripts are meant to work in a Linux environment, and were tested in Ubuntu 20.04.1 LTS.

The two scripts are:
- run.sh
- ct.sh (close all the open terminals except the one where this script is executed)

### - run.sh

This script will run the server and the client in different terminals, using the specified flags to adjust the testing cases:

#### Register Testing

With the flag -t, the script will in first place remove all data from the users.txt file, then compile and execute the server and at last execute 6 different gnome terminals, each one with a client running the register command with different usernames and passwords.
```bash
# Command to execute the script
./run.sh -t 

# Script code for this part

# t ) # Register Test

        # Erase all content of file for easier repeated testing
        truncate -s 0 main/data/users.txt

        fuser -k 1234/tcp; clear; javac main/Server.java; java main.Server unranked 2 2 2 &

        # Wait for two seconds
        sleep 2

        # Open 2 new terminals and run Client
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register zeDiogo apple1; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register eduSilva apple2; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register afonsoFarroco apple3; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register suzanaDiogo apple4; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register dianaMeireles apple5; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client register sergioDiogo apple6; exec bash"

        wait
      ;;
```

#### Play Testing

For this part, we can divide the test into two parts:

The first one will run in **unranked** mode, disregarding the players rank while trying to create game modes:
```bash
# Command to execute the script
./run.sh -u

u ) # Play Test for 2 games of 2 players each in unranked

            # Requirements ->
                # - 4 registered users

            fuser -k 1234/tcp; clear; javac main/Server.java; java main.Server unranked 2 2 2 &

            # Wait for two seconds
            sleep 2

            # Open 2 new terminals and run Client
            gnome-terminal -- bash -c "javac main/Client.java && java main/Client login zeDiogo apple1; exec bash"
            gnome-terminal -- bash -c "javac main/Client.java && java main/Client login eduSilva apple2; exec bash"
            gnome-terminal -- bash -c "javac main/Client.java && java main/Client login afonsoFarroco apple3; exec bash"
            gnome-terminal -- bash -c "javac main/Client.java && java main/Client login suzanaDiogo apple4; exec bash"

            wait
          ;;
```

The second part will run the **ranked** mode, where the players rank will be taken into account while trying to create game modes:
In order for this to work, set for example in the users.txt file the following ranks for the first 4 users:
- zeDiogo: 1200
- eduSilva: 1259
- afonsoFarroco: 1000
- suzanaDiogo: 1070
```bash
# Command to execute the script
./run.sh -r

r ) # Play Test for 2 games of 2 players each in ranked mode

        # Requirements ->
            # - 4 registered users
            # - Groups of users must have close ranking numbers, so we can see the matchmaking algorithm working, for example:
              # - zeDiogo (1) and eduSilva (2) -> Group 1 -> 1200 & 1259
              # - afonsoFarroco (3) and suzanaDiogo (4) -> Group 2 -> 1000 & 1070

        fuser -k 1234/tcp; clear; javac main/Server.java; java main.Server ranked 2 2 2 &

        # Wait for two seconds
        sleep 2

        # Open 2 new terminals and run Client
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client login zeDiogo apple1; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client login eduSilva apple2; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client login afonsoFarroco apple3; exec bash"
        gnome-terminal -- bash -c "javac main/Client.java && java main/Client login suzanaDiogo apple4; exec bash"

        wait
      ;;
```

#### Cleaning all the .class files

This part of the script will remove all the .class files from the /src/main folder, so that we can compile the code again without any problems.
```bash
# Command to execute the script
./run.sh -d
``