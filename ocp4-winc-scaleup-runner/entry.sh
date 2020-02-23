#!/bin/env bash

set -xe

# export RUNNER_WORKER_DIR=${WORKSPACE}/v4-testing-misc/v4-scaleup-next/ocp4-winc-scaleup-runner/${OCP_VERSION}
export RUNNER_WORKER_DIR=${WORKSPACE}/winc-scaleup/ocp4-winc-scaleup-runner/${OCP_VERSION}
export WINC_REPO="windows-machine-config-bootstrapper"
# export KUBECONFIG_DIR=${WORKSPACE}/installer

cd ${RUNNER_WORKER_DIR}
  # inventory generation
  ansible-playbook generate_inventory.yaml -v
  # export KUBECONFIG="${KUBECONFIG_DIR}/auth/kubeconfig"
  export KUBECONFIG="${WORKSPACE}/kubeconfig"
  cat inventory

  # Check windows connection
  ansible win -i inventory -m win_ping -v

  # TODO: setup go env here or in scaleup_pre_hook.yaml

  case "${OPERATION}" in
  "SCALEUP" )
    ansible-playbook -i inventory scaleup_pre_hook.yaml -v
    ansible-playbook -i inventory ${WORKSPACE}/${WINC_REPO}/tools/ansible/tasks/wsu/main.yml -v
    ansible-playbook -i inventory scaleup_post_hook.yaml -v
  ;;
  * )
    echo "Unknown operation => ${OPERATION} <=, possible ops are 'SCALEUP'"
  ;;
  esac
cd -