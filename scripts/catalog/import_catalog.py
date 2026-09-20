"""Generate reviewable, idempotent SQL; --apply backs up catalog tables and imports.

Uses the installed mysql/mysqldump clients; reads the existing database password
from MALL_DATASOURCE_PASSWORD or MALL_DB_PASSWORD. No credentials are written.
"""
import argparse
import hashlib
import html
import json
import os
import shutil
import subprocess
from collections import defaultdict, deque
from datetime import datetime
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / 'document/catalog/mi-20260911.json'
SQL_PATH = ROOT / 'document/sql/mall_catalog_mi_20260911.sql'
CATALOG_TABLES = ['pms_product', 'pms_sku_stock', 'pms_product_attribute_category',
                  'pms_product_attribute', 'pms_product_attribute_value',
                  'pms_product_category', 'sms_home_new_product', 'sms_home_recommend_product']


def quote(value):
    if value is None:
        return 'NULL'
    if isinstance(value, (int, Decimal)):
        return str(value)
    # MySQL UTF-8 hex literals are independent of SQL mode and quote escaping.
    return "CONVERT(X'" + str(value).encode('utf-8').hex() + "' USING utf8mb4)"


def insert_once(table, values, predicate):
    return (f"INSERT INTO {table} ({', '.join(values)}) SELECT "
            + ', '.join(values.values()) + f' WHERE NOT EXISTS (SELECT 1 FROM {table} WHERE {predicate});')


def generate_sql(document, base_url):
    groups = defaultdict(deque)
    for p in document['products']:
        groups[p['category_id']].append(p)
    # Spread the homepage across categories instead of filling it with phones.
    products = []
    while any(groups.values()):
        products.extend(g.popleft() for g in groups.values() if g)
    statements = [
        '-- Xiaomi public catalog snapshot, 2026-09-11. See document/catalog/README.md.',
        '-- New records only; existing products, orders and members are not overwritten.',
        'SET NAMES utf8mb4;', 'START TRANSACTION;',
        insert_once('pms_product_attribute_category',
                    {'name': quote('官网采集商品'), 'attribute_count': '1', 'param_count': '3'},
                    'name=' + quote('官网采集商品')),
        'SET @catalog_attr_category=(SELECT id FROM pms_product_attribute_category WHERE name=' + quote('官网采集商品') + ' LIMIT 1);',
    ]
    for index, (name, kind) in enumerate([('展示规格', 0), ('商品型号', 1), ('品牌', 1), ('价格说明', 1)]):
        statements += [insert_once('pms_product_attribute', {
            'product_attribute_category_id': '@catalog_attr_category', 'name': quote(name),
            'select_type': '1', 'input_type': '0', 'input_list': quote(''), 'sort': str(10-index),
            'filter_type': '0', 'search_type': '0', 'related_status': '0', 'hand_add_status': '1', 'type': str(kind),
        }, 'product_attribute_category_id=@catalog_attr_category AND name=' + quote(name)),
            f'SET @catalog_attr_{index}=(SELECT id FROM pms_product_attribute WHERE product_attribute_category_id=@catalog_attr_category AND name={quote(name)} LIMIT 1);']

    for index, p in enumerate(products):
        url = base_url.rstrip('/') + p['local_image_path']
        price = Decimal(p['price_cny'])
        price_kind = '官网参考起售价' if p['is_starting_price'] else '官网参考价格'
        price_note = f'{price_kind}，采集于{document["captured_on"]}，具体配置价格以官网为准'
        detail = (f'<h3>{html.escape(p["name"])}</h3><p>{html.escape(p["brief"])}</p>'
                  f'<p>{price_kind}：￥{price}。采集日期：{document["captured_on"]}。</p>'
                  '<p>本商城为实训演示，展示规格对应官网商品卡片，不代表已核验具体颜色或容量；库存为演示库存。</p>'
                  f'<p>品牌：小米；分类：{p["category_name"]}。</p>'
                  f'<img src="{html.escape(url, quote=True)}" style="width:100%;max-width:640px" />'
                  f'<p>资料来源：<a href="{html.escape(p["source_url"], quote=True)}">小米商城</a></p>')
        data = {
            'brand_id': 6, 'product_category_id': p['category_id'], 'name': p['name'],
            'pic': url, 'product_sn': p['product_sn'], 'delete_status': 0, 'publish_status': 1,
            'new_status': 1, 'recommand_status': 1, 'verify_status': 1, 'sort': 300-index,
            'sale': 0, 'price': price, 'promotion_price': price, 'gift_growth': 0, 'gift_point': 0,
            'use_point_limit': 0, 'sub_title': p['brief'], 'description': p['brief'],
            'original_price': Decimal(p['original_price_cny']), 'stock': p['demo_stock'], 'low_stock': 5,
            'unit': '件', 'weight': Decimal('0'), 'preview_status': 0, 'service_ids': '',
            'keywords': f'{p["category_name"]},小米,{p["name"]}',
            'note': f'官网采集20260911；{p["source_url"]}；{price_kind}；库存为演示数据',
            'album_pics': '', 'detail_title': p['name'], 'detail_desc': p['brief'],
            'detail_html': detail, 'detail_mobile_html': detail, 'promotion_per_limit': 0,
            'promotion_type': 0, 'brand_name': '小米', 'product_category_name': p['category_name'],
        }
        values = {k: quote(v) for k, v in data.items()}
        values['product_attribute_category_id'] = '@catalog_attr_category'
        statements += [f'-- {p["category_name"]}: {p["name"]}; CNY {price}',
            insert_once('pms_product', values, 'product_sn=' + quote(p['product_sn'])),
            'SET @catalog_product=(SELECT id FROM pms_product WHERE product_sn=' + quote(p['product_sn']) + ' LIMIT 1);',
            insert_once('pms_sku_stock', {
                'product_id': '@catalog_product', 'sku_code': quote(p['product_sn'] + '-01'),
                'price': quote(price), 'stock': str(p['demo_stock']), 'low_stock': '5', 'pic': quote(url),
                'sale': '0', 'promotion_price': quote(price), 'lock_stock': '0',
                'sp_data': quote(json.dumps([{'key': '展示规格', 'value': p['sku_specification']}], ensure_ascii=False)),
            }, 'sku_code=' + quote(p['product_sn'] + '-01'))]
        for ai, value in enumerate([p['sku_specification'], p['name'], '小米', price_note]):
            if len(value) > 64:
                raise ValueError('Attribute value exceeds schema limit')
            statements.append(insert_once('pms_product_attribute_value', {
                'product_id': '@catalog_product', 'product_attribute_id': f'@catalog_attr_{ai}', 'value': quote(value)
            }, f'product_id=@catalog_product AND product_attribute_id=@catalog_attr_{ai}'))
        for table in ['sms_home_new_product', 'sms_home_recommend_product']:
            statements.append(insert_once(table, {'product_id': '@catalog_product', 'product_name': quote(p['name']),
                              'recommend_status': '1', 'sort': str(300-index)}, 'product_id=@catalog_product'))
    categories = ','.join(str(x) for x in groups)
    statements += [f'UPDATE pms_product_category SET show_status=1 WHERE id IN ({categories});',
        f'UPDATE pms_product_category c SET product_count=(SELECT COUNT(*) FROM pms_product p WHERE p.product_category_id=c.id AND p.delete_status=0 AND p.publish_status=1) WHERE c.id IN ({categories});',
        'COMMIT;',
        "SELECT COUNT(*) AS imported_product_count FROM pms_product WHERE product_sn LIKE 'WEB-MI-20260911-%';"]
    SQL_PATH.write_text('\n'.join(statements) + '\n', encoding='utf-8')
    return products


def apply_import(document, args):
    env = os.environ.copy()
    password = env.get('MALL_DATASOURCE_PASSWORD') or env.get('MALL_DB_PASSWORD')
    if not password:
        raise RuntimeError('Set MALL_DB_PASSWORD or MALL_DATASOURCE_PASSWORD first')
    env['MYSQL_PWD'] = password
    mysql = shutil.which('mysql')
    dump = shutil.which('mysqldump')
    if not mysql or not dump:
        raise RuntimeError('mysql and mysqldump must be installed')
    for p in document['products']:
        image_path = ROOT / 'mall-portal/src/main/resources/static' / p['local_image_path'].lstrip('/')
        if not p.get('local_image_sha256') or hashlib.sha256(image_path.read_bytes()).hexdigest() != p['local_image_sha256']:
            raise RuntimeError(f'Image missing or mismatched: {p["name"]}')
    options = [f'--host={args.db_host}', f'--user={args.db_user}', '--default-character-set=utf8mb4']
    def query(sql):
        return subprocess.check_output([mysql, *options, args.database, '--batch', '--skip-column-names', '-e', sql], env=env).decode('utf-8').strip()
    if query('SELECT name FROM pms_brand WHERE id=6') != '小米':
        raise RuntimeError('This catalog expects the existing mall Xiaomi brand id 6')
    for p in document['products']:
        if query('SELECT name FROM pms_product_category WHERE id=' + str(p['category_id'])) != p['category_name']:
            raise RuntimeError('Category mapping mismatch')
    backup = ROOT / 'output/catalog-backup' / datetime.now().strftime('%Y%m%d-%H%M%S')
    backup.mkdir(parents=True, exist_ok=False)
    subprocess.run([dump, *options, '--single-transaction', '--no-tablespaces', '--set-gtid-purged=OFF',
                    f'--result-file={backup / "catalog-before.sql"}', args.database, *CATALOG_TABLES], env=env, check=True)
    with SQL_PATH.open('rb') as sql:
        result = subprocess.run([mysql, *options, args.database, '--batch'], stdin=sql, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    print(result.stdout.decode('utf-8'))
    print('Backup:', backup / 'catalog-before.sql')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--image-base-url', default='https://mall.huahang.me')
    parser.add_argument('--db-host', default='127.0.0.1')
    parser.add_argument('--db-user', default=os.getenv('MALL_DATASOURCE_USERNAME', 'root'))
    parser.add_argument('--database', default='mall')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    document = json.loads(MANIFEST.read_text(encoding='utf-8'))
    products = generate_sql(document, args.image_base_url)
    print(f'Prepared {len(products)} products: {SQL_PATH}')
    if args.apply:
        apply_import(document, args)
