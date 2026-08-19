"""生成激活码并写入 keycode.db（全功能模式管理员专用）。

用法：python -m scripts.gen_keycode [数量]
示例：python -m scripts.gen_keycode 1000   # 生成 1000 个激活码
"""
import secrets
import sys

from app.keycode import create_code, init_keycode_db


def gen_code() -> str:
    """格式：12 位 16 进制随机数（如 3f9a1c2b8e4d）。"""
    return secrets.token_hex(6)


def main() -> None:
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    init_keycode_db()
    codes = []
    for _ in range(n):
        c = gen_code()
        create_code(c)
        codes.append(c)
    print(f'已生成 {n} 个激活码（已写入 keycode.db）：')
    for c in codes:
        print(f'  {c}')


if __name__ == '__main__':
    main()
