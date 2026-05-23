# JBuster 🚀

A lightning-fast, highly concurrent directory and file buster built from scratch using modern **Java 21+ Virtual Threads**.

Designed as a lightweight alternative to tools like Gobuster and ffuf, JBuster leverages Project Loom's virtual threads to fire hundreds of concurrent network requests with almost zero memory overhead. It is specifically optimized for CTF environments like Hack The Box (HTB) and live bug bounty targets.

## ✨ Features (Phase 1)

* **Massive Concurrency:** Uses `Executors.newVirtualThreadPerTaskExecutor()` to handle thousands of I/O bound tasks seamlessly without bogging down your CPU.
* **Smart Blocklisting:** Filter out the noise by ignoring specific HTTP status codes (`-x`) and response sizes (`-Ss`) to avoid the dreaded "Catch-All" 200 OK trap.
* **Dynamic Extension Expansion:** Feed it a wordlist and a list of extensions (`-e php,txt`), and JBuster will dynamically generate and test all URL variations in real-time using Java Streams.

---

## 🛠️ Installation & Build Guide

### Prerequisites

* Java Development Kit (JDK) 21 or newer.

### 1. Compile the Source

Navigate to the project directory and compile the Java file:

```bash
javac Main.java

```

### 2. Package into an Executable JAR

Create a manifest file and package the compiled class:

```bash
echo "Main-Class: Main" > manifest.txt
jar cfm jbuster.jar manifest.txt Main.class

```

### 3. Make it a Global Command (Linux/macOS)

Create a bash wrapper so you can run `jbuster` from anywhere:

```bash
nano ~/.local/bin/jbuster

```

Paste the following (update the path to match your machine):

```bash
#!/bin/bash
java -jar /home/gabbar/projects/jbuster/jbuster.jar "$@"

```

Make it executable:

```bash
chmod +x ~/.local/bin/jbuster

```

---

## 💻 Usage

### Basic Example

```bash
jbuster -u https://example.com -w /usr/share/wordlists/dirb/common.txt

```

### Advanced CTF Example

Test for hidden PHP and text files, use 150 virtual threads, skip all 404s, and ignore custom 503 error pages that are exactly 162 bytes in size:

```bash
jbuster -u http://10.10.11.55 -w wordlist.txt -t 150 -e php,txt -x 404,503 -Ss 162

```

### Flags

```text
  -h : help to spil out all the commands
  -w : path to the wordlist
  -u : url link
  -t : max threads, default 20
  -Ss : size skip [-Ss 452,352,600,900] as size of the page
  -x : status skip [-x 301,302,400,401,402]
  -e : extensions [-e php,html,txt,bok] as use need
  -hv : 1.1 or 2, default 1.1

```

---

## 🗺️ Roadmap (Phase 2)

The core engine is rock solid. The next phase of development will focus on adding professional penetration testing features:

* [ ] **Custom Header Injection (`-H`):** Support for passing Session Cookies or Authorization Bearer tokens for authenticated endpoint fuzzing.
* [ ] **Virtual Host (VHost) Routing (`-vhost`):** Bypass Java's restricted header rules to inject wordlists directly into the HTTP `Host` header for subdomain/VHost discovery.
* [ ] **Save Output to File (`-o`):** Implement thread-safe writers to log discovered endpoints to a `.txt` or `.json` file for reporting.
* [ ] **Rate Limiting / Delay (`-d`):** Add millisecond delays between requests to bypass Web Application Firewalls (WAFs) without getting IP banned.
* [ ] **Live Progress Tracker:** A dynamic terminal counter (e.g., `[ 4500 / 100000 ]`) using `AtomicInteger` to track scan completion in real-time.
* [x] **HTB/CTF Optimized:** Forces HTTP/1.1 connections to prevent the infamous HTTP/2 handshake hang on older/custom target servers.
