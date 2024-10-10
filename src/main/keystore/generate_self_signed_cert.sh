#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

tmpDir=$(mktemp -d)
trap 'rm -rf $tmpDir' EXIT

cat > "$tmpDir/req.cnf" <<EOF
[ req ]
default_bits 		= 2048
prompt 				= no
distinguished_name 	= req_distinguished_name
x509_extensions 	= req_ext

[ req_distinguished_name ]
countryName         	= US
stateOrProvinceName 	= NY
localityName 			= New York
organizationName    	= Jenkins
organizationalUnitName 	= Test
commonName          	= Self-Signed CA
emailAddress			= noreply@jenkins.io

[ req_ext ]
keyUsage				= digitalSignature
basicConstraints		= CA:false
subjectAltName 			= @alternate_names
subjectKeyIdentifier 	= hash

[ alternate_names ]
DNS.1         = localhost
EOF

mkdir -p "$tmpDir/output"
key="$tmpDir/output/key.pem"
cert="$tmpDir/output/cert.pem"
certP12="$tmpDir/output/cert.p12"
echo "Generate self-signed cert for localhost for 100 years"
openssl req -newkey rsa:2048 -nodes -keyout "$key" -x509 -days 36500 -out "$cert" -config "$tmpDir/req.cnf"
echo "Generate PKCS12 keystore"
openssl pkcs12 -inkey "$key" -in "$cert" -export -out "$certP12" -passout pass:changeit -passin pass:changeit
echo "Copying generated resources to src/main/resources"
mkdir -p src/main/resources/https
cp "$cert" src/main/resources/https/test-cert.pem
cp "$certP12" src/main/resources/https/test-keystore.p12
