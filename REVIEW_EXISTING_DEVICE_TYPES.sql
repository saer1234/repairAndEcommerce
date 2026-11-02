-- Read-only checks. No data is modified by this file.
USE repair_management;

-- Items entered with component names in device_type instead of Phone/Tablet/etc.
SELECT id, company_id, sku, name, category, device_type, device_brand
FROM items
WHERE LOWER(TRIM(device_type)) IN (
 'screen','display','battery','charging port','charging cable',
 'charging flex','speaker','microphone','back cover','rear cover',
 'housing','motherboard','flex cable','camera module','camera lens',
 'charging connector','power button','vibration motor','sim tray','antenna'
)
ORDER BY company_id, sku;

-- Entries in verified-fit compatibility records whose device type is actually a component category.
SELECT c.item_id, i.sku, c.device_type, c.device_brand, c.device_model
FROM item_compatibilities c
JOIN items i ON i.id = c.item_id
WHERE LOWER(TRIM(c.device_type)) IN (
 'screen','display','battery','charging port','charging cable',
 'charging flex','speaker','microphone','back cover','rear cover',
 'housing','motherboard','flex cable','camera module','camera lens',
 'charging connector','power button','vibration motor','sim tray','antenna'
)
ORDER BY i.company_id, i.sku;

-- Categories previously overwritten with device types; review individually.
SELECT id, company_id, sku, name, category, device_type
FROM items
WHERE LOWER(TRIM(category)) = LOWER(TRIM(device_type))
ORDER BY company_id, sku;
