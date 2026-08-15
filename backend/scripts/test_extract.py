"""本地调试：生成模拟支付截图 + POST /api/ai/extract/ 验证识别链路。"""
import io
import json
import os
import sys
import urllib.request

from PIL import Image, ImageDraw, ImageFont

BASE = os.getenv('TEST_URL', 'http://127.0.0.1:8000')
OUT = os.path.join(os.environ['TEMP'], 'pay_test.png')


def make_image() -> None:
    w, h = 720, 900
    img = Image.new('RGB', (w, h), 'white')
    d = ImageDraw.Draw(img)
    font = lambda s: ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', s)
    d.text((240, 60), '支付成功', fill='black', font=font(44))
    d.text((60, 200), '美团外卖', fill='black', font=font(40))
    d.text((60, 300), '¥ 35.00', fill='black', font=font(56))
    d.text((60, 420), '2026-08-15 12:30', fill='gray', font=font(32))
    d.text((60, 500), '备注：午餐', fill='gray', font=font(32))
    d.text((60, 700), '交易单号 202608150012345678', fill='gray', font=font(26))
    img.save(OUT)
    print('saved:', OUT, img.size)


def test_extract() -> None:
    boundary = '----testboundary123'
    with open(OUT, 'rb') as f:
        data = f.read()
    body = io.BytesIO()
    body.write(f'--{boundary}\r\n'.encode())
    body.write(b'Content-Disposition: form-data; name="file"; filename="pay_test.png"\r\n')
    body.write(b'Content-Type: image/png\r\n\r\n')
    body.write(data)
    body.write(f'\r\n--{boundary}--\r\n'.encode())
    req = urllib.request.Request(
        f'{BASE}/api/ai/extract/',
        data=body.getvalue(),
        headers={'Content-Type': f'multipart/form-data; boundary={boundary}'},
        method='POST',
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            print('HTTP', resp.status)
            print(json.dumps(json.loads(resp.read().decode()), ensure_ascii=False, indent=2))
    except urllib.error.HTTPError as e:
        print('HTTPError', e.code)
        print(e.read().decode('utf-8', 'ignore'))


if __name__ == '__main__':
    make_image()
    test_extract()
