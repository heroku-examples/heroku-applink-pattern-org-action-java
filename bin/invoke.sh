#!/bin/bash

# Check if required arguments are provided
if [ -z "$1" ] || [ -z "$2" ]; then
    echo "Usage: $0 <salesforce-org-alias> <payload-json>"
    exit 1
fi

# Set variables from script arguments
SF_ORG_ALIAS="$1"
PAYLOAD_JSON="$2"

# Fetch Salesforce org details using the Salesforce CLI
SF_ORG_INFO=$(sf org display -u "$SF_ORG_ALIAS" --json 2>/dev/null)

# Check if the command was successful
if [ $? -ne 0 ]; then
    echo "Error: Unable to fetch Salesforce org details for alias '$SF_ORG_ALIAS'. Ensure Salesforce CLI is installed and authenticated."
    exit 1
fi

# Extract necessary fields from JSON
ACCESS_TOKEN=$(echo "$SF_ORG_INFO" | jq -r '.result.accessToken')
API_VERSION=$(echo "$SF_ORG_INFO" | jq -r '.result.apiVersion')
ORG_ID=$(echo "$SF_ORG_INFO" | jq -r '.result.id')
ORG_DOMAIN_URL=$(echo "$SF_ORG_INFO" | jq -r '.result.instanceUrl')
USERNAME=$(echo "$SF_ORG_INFO" | jq -r '.result.username')

# Validate extracted values
if [ -z "$ACCESS_TOKEN" ] || [ -z "$API_VERSION" ] || [ -z "$ORG_ID" ] || [ -z "$ORG_DOMAIN_URL" ] || [ -z "$USERNAME" ]; then
    echo "Error: Missing required Salesforce org details. Ensure the org is authenticated."
    exit 1
fi

# Construct the x-client-context JSON
CLIENT_CONTEXT_JSON=$(cat <<EOF
{
  "accessToken": "$ACCESS_TOKEN",
  "apiVersion": "$API_VERSION",
  "requestId": "req-$(uuidgen)",
  "namespace": "demo",
  "orgId": "$ORG_ID",
  "orgDomainUrl": "$ORG_DOMAIN_URL",
  "userContext": {
    "userId": "0055g00000EXAMPLE",
    "username": "$USERNAME"
  }
}
EOF
)

# Encode the JSON into Base64
ENCODED_CLIENT_CONTEXT=$(echo -n "$CLIENT_CONTEXT_JSON" | base64)

# Define the API URL
API_URL="http://localhost:8080/api/generatequote"

# Make the request
RESPONSE=$(curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -H "x-client-context: $ENCODED_CLIENT_CONTEXT" \
  -d "$PAYLOAD_JSON")

# Print response
echo "Response from server:"
echo "$RESPONSE"
