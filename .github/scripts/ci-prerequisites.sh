# Reclaim disk space, otherwise we have too little free space at the start of a job
# This is a workaround for: https://github.com/quarkusio/quarkus/issues/40118

time sudo docker image prune --all --force || true
time sudo rm -rf /usr/share/dotnet || true
time sudo rm -rf /usr/share/swift || true
# Remove Android
time sudo rm -rf /usr/local/lib/android || true
# Remove Haskell
time sudo rm -rf /opt/ghc || true
time sudo rm -rf /usr/local/.ghcup || true
# Remove pipx
time sudo rm -rf /opt/pipx || true
# Remove Rust
time sudo rm -rf /usr/share/rust || true
# Remove Go
time sudo rm -rf /usr/local/go || true
# Remove miniconda
time sudo rm -rf /usr/share/miniconda || true
# Remove powershell
time sudo rm -rf /usr/local/share/powershell || true
# Remove Google Cloud SDK
time sudo rm -rf /usr/lib/google-cloud-sdk || true

# Remove infrastructure things that are unused and take a lot of space
time sudo rm -rf /opt/hostedtoolcache/CodeQL || true
time sudo rm -rf /imagegeneration/installers/go-* || true
time sudo rm -rf /imagegeneration/installers/node-* || true
time sudo rm -rf /imagegeneration/installers/python-* || true
