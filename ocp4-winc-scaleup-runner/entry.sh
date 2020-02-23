#!/bin/env bash

set -xe

# export RUNNER_WORKER_DIR=${WORKSPACE}/v4-testing-misc/v4-scaleup-next/ocp4-winc-scaleup-runner/${OCP_VERSION}
export RUNNER_WORKER_DIR=${WORKSPACE}/ocp4-winc-scaleup-runner/${OCP_VERSION}
export WINC_REPO="windows-machine-config-bootstrapper"

cd ${RUNNER_WORKER_DIR}
  # wget KUBECONFIG_URL
  # export KUBECONFIG="${RUNNER_WORKER_DIR}/kubeconfig"
  git clone https://github.com/openshift/${WINC_REPO} --depth=1

  # TODO: add multply workes parallel
  echo ${WINC_WORKERS}

  # inventory generation
  ansible-playbook generate_inventory.yaml -v
  cat inventory

  # Check windows connection
  ansible win -i inventory -m win_ping -v

  case "${OPERATION}" in
  "SCALEUP" )
    ansible-playbook -i inventory scaleup_pre_hook.yaml -v
    ansible-playbook -i inventory ${RUNNER_WORKER_DIR}/${WINC_REPO}/tools/ansible/tasks/wsu/main.yml -v
    ansible-playbook -i inventory scaleup_post_hook.yaml -v
  ;;
  * )
    echo "Unknown operation => ${OPERATION} <=, possible ops are 'SCALEUP'"
  ;;
  esac
cd -