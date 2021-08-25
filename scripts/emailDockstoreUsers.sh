#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset

# How to use:
# ./emailDockstoreUsers.sh <DOCKSTORE_TOKEN> <EMAIL_APP_PASSWORD> <SENDER_EMAIL>
# <EMAIL_APP_PASSWORD> -> To send an email using a gmail account with 2 factor Authorization, you must create an app password https://support.google.com/accounts/answer/185833?hl=en
# Must be a Dockstore admin
#
# Additionally, a file named emailBody.txt is required. This file should contain whatever contents you want and preferably would begin with
# Subject: <ENTER SUBJECT LINE HERE>
# Otherwise, the email will be sent without a subject line.
#
# This script:
# gets a subset of user info for all users from a Dockstore endpoint
# Creates a text file 'mail.txt' that includes the FROM, TO, and body portions of the email.
# Sends the email to every email in the list

DOCKSTORE_TOKEN=$1
EMAIL_APP_PASSWORD=$2
SENDER_EMAIL=$3

FROM_EMAIL="From: \"Dockstore\" <${SENDER_EMAIL}>"

curl -X GET "http://dockstore.org/api/users/emails" -H "accept: application/json" -H "Authorization: Bearer ${DOCKSTORE_TOKEN}" | jq -r '.[].thirdPartyEmail' > dockstoreEmails.txt



while IFS= read -r email ; do
  if [ $email != "null" ] ; then
    TO_EMAIL="To: <${email}>"
    echo "$FROM_EMAIL" > mail.txt
    echo "$TO_EMAIL" >> mail.txt
    cat emailBody.txt >> mail.txt
  fi

  curl --ssl-reqd \
  --url 'smtps://smtp.gmail.com:465' \
  --user "${SENDER_EMAIL}:${EMAIL_APP_PASSWORD}" \
  --mail-from ${SENDER_EMAIL} \
  --mail-rcpt $email \
  --upload-file mail.txt
done < dockstoreEmails.txt
