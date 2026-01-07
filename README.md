# Git Implementation in Java (Codecrafters)

A from-scratch implementation of core Git functionality in Java, built as part of the Codecrafters Git challenge.  
This project recreates Git internals without using any Git libraries, with a focus on understanding how Git works at a low level.

---

## Features

- Initialize a Git repository (`git init`)
- Create and store Git objects:
  - Blob objects
  - Tree objects
  - Commit objects
- SHA-1 content-addressable storage
- Zlib compression and decompression
- Tree traversal and directory structure reconstruction
- Commit creation with parent linking
- Reference and `HEAD` management
- Read and inspect Git objects
- Full `git clone` implementation using Git Smart HTTP
- Reference discovery via `info/refs`
- pkt-line parsing
- Packfile download and unpacking
- Delta object resolution
- Compatibility with the native Git CLI

---

## What This Project Covers

This project rebuilds Git step by step to understand its internal design, including:

- Git object model (blob, tree, commit)
- `.git` directory layout
- Loose objects vs packfiles
- Git Smart HTTP protocol
- Packfile format and delta compression
- Content-addressable storage using SHA-1
- Efficient network transfer of Git data

All components are implemented manually in Java without relying on existing Git tooling or libraries.

---

## 🛠 Tech Stack

- **Language:** Java
- **Networking:** Java HTTP Client
- **Compression:** Zlib
- **Hashing:** SHA-1
- **Protocol:** Git Smart HTTP

---

## How to Run

### Compile the Source Code

From the repository root:

```bash
javac src/main/java/*.java
