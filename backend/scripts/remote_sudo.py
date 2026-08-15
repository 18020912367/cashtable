"""远程执行助手：用 SSH 密钥登录云端，以 sudo -S 执行命令（密码经环境变量传入）。

用法：remote_sudo.py "<命令>"
"""
import os
import shlex
import sys

import paramiko

HOST = '124.223.74.139'
PORT = 22
USER = 'auto001'
KEY = os.path.expanduser('~/.ssh/id_ed25519')
SUDO_PASS = os.environ['SERVER_PASSWORD']
CMD = sys.argv[1]

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, port=PORT, username=USER, key_filename=KEY, timeout=15)

# 整条命令包进 bash -c，确保后续命令也以 root 执行
stdin, stdout, stderr = client.exec_command(
    'sudo -S bash -c ' + shlex.quote(CMD), timeout=180
)
stdin.write(SUDO_PASS + '\n')
stdin.flush()
out = stdout.read().decode()
err = stderr.read().decode()
print(out)
if err.strip():
    print('STDERR:', err)
client.close()
