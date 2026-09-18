import sqlite3
conn = sqlite3.connect(r'E:/work/Android Project/RailFocus/app/src/main/assets/databases/rail_focus.db')
c = conn.cursor()

c.execute('SELECT COUNT(*) FROM edges')
total = c.fetchone()[0]
print(f'Total edges: {total}')

c.execute("SELECT from_id, to_id, distance_km FROM edges WHERE from_id = '南京南'")
nanjing_out = c.fetchall()
print(f'\n南京南 outgoing: {len(nanjing_out)}')
for r in nanjing_out[:10]:
    print(f'  {r[0]} -> {r[1]} ({r[2]}km)')

c.execute("SELECT from_id, to_id, distance_km FROM edges WHERE to_id = '南京南'")
nanjing_in = c.fetchall()
print(f'南京南 incoming: {len(nanjing_in)}')
for r in nanjing_in[:10]:
    print(f'  {r[0]} -> {r[1]} ({r[2]}km)')

# Check if edges have reverse pairs
c.execute("""
SELECT e1.from_id, e1.to_id FROM edges e1
WHERE EXISTS (
    SELECT 1 FROM edges e2
    WHERE e2.from_id = e1.to_id AND e2.to_id = e1.from_id
)
LIMIT 5
""")
pairs = c.fetchall()
print(f'\nBidirectional pairs found: {len(pairs)}')
for r in pairs:
    print(f'  {r[0]} <-> {r[1]}')

# Check how many edges have NO reverse
c.execute("""
SELECT COUNT(*) FROM edges e1
WHERE NOT EXISTS (
    SELECT 1 FROM edges e2
    WHERE e2.from_id = e1.to_id AND e2.to_id = e1.from_id
)
""")
no_reverse = c.fetchone()[0]
print(f'\nEdges WITHOUT reverse pair: {no_reverse} / {total}')

# Sample some stations with outgoing edges
c.execute("""
SELECT from_id, COUNT(*) as cnt
FROM edges
GROUP BY from_id
ORDER BY cnt DESC
LIMIT 5
""")
print('\nTop stations by outgoing edges:')
for r in c.fetchall():
    print(f'  {r[0]}: {r[1]} edges')

conn.close()
