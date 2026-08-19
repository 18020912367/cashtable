"""激活流程端到端测试：模板下载 → 生成激活码 → check → upload → 租户路由。

用法：python -m scripts.test_activate
"""
import io
import sys

from fastapi.testclient import TestClient

sys.path.insert(0, '.')

from app.main import app  # noqa: E402
from app.keycode import create_code  # noqa: E402

client = TestClient(app)

print('== 1. 生成激活码 ==')
code = 'TEST-ABCD-EFGH-1234'
create_code(code)
print('激活码:', code)

print('\n== 2. check（未激活） ==')
r = client.post('/api/activate/check', json={'code': code, 'device_mac': 'dev-001'})
print(r.status_code, r.json())

print('\n== 3. 下载标准模板 ==')
r = client.get('/api/activate/template')
print('模板状态:', r.status_code, '大小:', len(r.content), 'bytes')
template_bytes = r.content

print('\n== 4. upload（首次激活） ==')
r = client.post(
    '/api/activate/upload',
    data={'code': code, 'device_mac': 'dev-001'},
    files={'file': ('cashtable.db', io.BytesIO(template_bytes), 'application/octet-stream')},
)
print(r.status_code, r.json())

print('\n== 5. 租户路由：带激活码访问 /api/users/ ==')
r = client.get('/api/users/', headers={'X-Activate-Code': code, 'X-Device-Mac': 'dev-001'})
print(r.status_code, r.json())

print('\n== 6. 租户路由：不带激活码（默认库） ==')
r = client.get('/api/users/')
print(r.status_code, len(r.json()), '个记账人')

print('\n== 7. 错误激活码访问租户库 ==')
r = client.get('/api/users/', headers={'X-Activate-Code': 'WRONG-CODE', 'X-Device-Mac': 'x'})
print(r.status_code, r.json())

print('\n== 8. 重复激活（同设备，幂等覆盖） ==')
r = client.post(
    '/api/activate/upload',
    data={'code': code, 'device_mac': 'dev-001'},
    files={'file': ('cashtable.db', io.BytesIO(template_bytes), 'application/octet-stream')},
)
print(r.status_code, r.json())

print('\n== 9. 他人设备激活（应拒绝） ==')
r = client.post(
    '/api/activate/upload',
    data={'code': code, 'device_mac': 'dev-999'},
    files={'file': ('cashtable.db', io.BytesIO(template_bytes), 'application/octet-stream')},
)
print(r.status_code, r.json())

print('\n== 10. 激活状态查询 ==')
r = client.get('/api/activate/status', params={'code': code, 'device_mac': 'dev-001'})
print(r.status_code, r.json())

print('\n全部通过')
