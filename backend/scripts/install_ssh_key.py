"""一次性脚本：把本地 SSH 公钥装到云端 authorized_keys（密码经环境变量传入）。"""
import os

import paramiko

HOST = '124.223.74.139'
PORT = 22
USER = os.environ['SERVER_ACCOUNT']
PASSWORD = os.environ['SERVER_PASSWORD']
PUBKEY = 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIDnI23RYVPwU6C9AkX1ONSMgpeYyb3my56sEPLvsTjUT cashtable-ops'

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=15)

cmd = (
    "mkdir -p ~/.ssh && chmod 700 ~/.ssh && "
    "grep -qF 'cashtable-ops' ~/.ssh/authorized_keys 2>/dev/null || "
    f"echo '{PUBKEY}' >> ~/.ssh/authorized_keys; "
    "chmod 600 ~/.ssh/authorized_keys && tail -n 1 ~/.ssh/authorized_keys"
)
_, stdout, stderr = client.exec_command(cmd)
print('STDOUT:', stdout.read().decode())
print('STDERR:', stderr.read().decode())
client.close()
print('DONE')
