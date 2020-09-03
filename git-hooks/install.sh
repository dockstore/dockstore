#!/bin/bash
hookspath="$(git config --get core.hookspath)"
if [ -n $hookspath ]
then
  # remove any other hooks path configs
  git config --unset-all core.hookspath
fi
git secrets --register-aws
git config --add core.hookspath git-hooks/
