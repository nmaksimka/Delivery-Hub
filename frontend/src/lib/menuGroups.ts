import type { MenuItem } from '../api/restaurants';

export type MenuGroup = {
  id: string;
  title: string;
  items: MenuItem[];
};

const uncategorized = 'Другое';

export function groupMenu(items: MenuItem[]): MenuGroup[] {
  const byTitle = new Map<string, MenuItem[]>();

  items.forEach((item) => {
    const title = item.category?.trim() || uncategorized;
    const group = byTitle.get(title);
    if (group) {
      group.push(item);
    } else {
      byTitle.set(title, [item]);
    }
  });

  const titles = [...byTitle.keys()].filter((title) => title !== uncategorized);
  if (byTitle.has(uncategorized)) {
    titles.push(uncategorized);
  }

  return titles.map((title, index) => ({
    id: `menu-group-${index + 1}`,
    title,
    items: byTitle.get(title) ?? [],
  }));
}
