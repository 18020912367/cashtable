"""生成/管理激活码并写入 keycode.db（全功能模式管理员专用）。

用法：
  python -m scripts.gen_keycode [数量] [备注]   # 生成激活码（默认 1 个，备注可选）
  python -m scripts.gen_keycode --set 码 备注    # 设置已有激活码的备注
  python -m scripts.gen_keycode --list [N]      # 查看最近 N 个激活码（默认 20）

示例：
  python -m scripts.gen_keycode 1000            # 生成 1000 个激活码
  python -m scripts.gen_keycode 50 张三         # 生成 50 个并备注「张三」
  python -m scripts.gen_keycode --set 3f9a1c2b8e4d 李四
  python -m scripts.gen_keycode --list
"""
import secrets
import sys

from app.keycode import (
    ActivationCode,
    create_code,
    init_keycode_db,
    set_remark as set_code_remark,
)

USAGE = __doc__


def gen_code() -> str:
    """格式：12 位 16 进制随机数（如 3f9a1c2b8e4d）。"""
    return secrets.token_hex(6)


def gen_codes(n: int, remark: str) -> None:
    init_keycode_db()
    codes = []
    for _ in range(n):
        c = gen_code()
        create_code(c, remark=remark)
        codes.append(c)
    suffix = f'（备注：{remark}）' if remark else ''
    print(f'已生成 {n} 个激活码（已写入 keycode.db）{suffix}：')
    for c in codes:
        print(f'  {c}')


def list_codes(limit: int) -> None:
    from app.keycode import _keycode_session, _ensure_init

    _ensure_init()
    db = _keycode_session()
    try:
        rows = (
            db.query(ActivationCode)
            .order_by(ActivationCode.code.desc())
            .limit(limit)
            .all()
        )
    finally:
        db.close()
    if not rows:
        print('keycode.db 中暂无激活码。')
        return
    print(f'最近 {len(rows)} 个激活码（码 / 备注 / 状态 / 激活时间）：')
    for r in rows:
        status = '已激活' if r.is_activated == 1 else '未激活'
        print(f'  {r.code}  {r.remark or "-"}  {status}  {r.activated_at or "-"}')


def main() -> None:
    argv = sys.argv[1:]
    if not argv:
        gen_codes(1, '')
        return
    if argv[0] in ('-h', '--help'):
        print(USAGE)
        return
    if argv[0] == '--set':
        if len(argv) < 3:
            print('用法：python -m scripts.gen_keycode --set <激活码> <备注>')
            sys.exit(1)
        ok = set_code_remark(argv[1], argv[2])
        print(f'{"已更新" if ok else "未找到"}激活码 {argv[1]} 的备注：{argv[2]}')
        if not ok:
            sys.exit(1)
        return
    if argv[0] == '--list':
        limit = int(argv[1]) if len(argv) > 1 else 20
        list_codes(limit)
        return
    n = int(argv[0])
    remark = argv[1] if len(argv) > 1 else ''
    gen_codes(n, remark)


if __name__ == '__main__':
    main()
