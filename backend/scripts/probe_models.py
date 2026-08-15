"""探测不同视觉模型的可用性（本地调试用）。"""
import base64
import json
import os
import urllib.request
from urllib.error import HTTPError

KEY = os.getenv('LLMAPIKEY', 'd423ddf8106a46fca4462ed53acd1d3e.hWsCuUvjD6XNVXit')
URL = 'https://open.bigmodel.cn/api/paas/v4/chat/completions'
IMG = os.path.expandvars(r'%TEMP%\pay_test.png')

with open(IMG, 'rb') as f:
    b64 = base64.b64encode(f.read()).decode()

for model in ['glm-4.6v-flash', 'glm-4v-flash', 'glm-4v-plus']:
    payload = {
        'model': model,
        'messages': [{'role': 'user', 'content': [
            {'type': 'text', 'text': '提取支付截图中的交易时间、内容、类型、金额，只输出JSON'},
            {'type': 'image_url', 'image_url': {'url': f'data:image/png;base64,{b64}'}},
        ]}],
        'temperature': 0.1,
    }
    req = urllib.request.Request(
        URL, data=json.dumps(payload).encode('utf-8'),
        headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {KEY}'},
        method='POST')
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            print(model, 'OK:', r.read().decode('utf-8')[:300])
    except HTTPError as e:
        print(model, 'FAIL:', e.code, e.read().decode('utf-8')[:300])
    except Exception as e:
        print(model, 'ERR:', e)
