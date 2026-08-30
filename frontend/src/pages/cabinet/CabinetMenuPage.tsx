import { useState, type FormEvent } from 'react';
import { useParams } from 'react-router';
import type { MenuItemInput } from '../../api/cabinet';
import { errorMessage } from '../../api/http';
import { useCabinetMenu, useChangeMenu, useUploadMenuItemImage } from '../../api/queries';
import type { MenuItem } from '../../api/restaurants';
import { Button } from '../../components/Button';
import { Field } from '../../components/Field';
import { StateMessage } from '../../components/StateMessage';
import { formatPrice } from '../../lib/format';
import styles from '../Management.module.css';

type Draft = { id: number | null; name: string; price: string; category: string };

const emptyDraft: Draft = { id: null, name: '', price: '', category: '' };

function toInput(draft: Draft, item: MenuItem | undefined): MenuItemInput | null {
  const price = Number(draft.price.replace(',', '.'));
  if (!draft.name.trim() || !Number.isFinite(price) || price <= 0) {
    return null;
  }
  return {
    name: draft.name.trim(),
    description: item?.description ?? null,
    price,
    category: draft.category.trim() || null,
    available: item?.available ?? true,
  };
}

export function CabinetMenuPage() {
  const restaurantId = Number(useParams().restaurantId);
  const menu = useCabinetMenu(restaurantId);
  const change = useChangeMenu(restaurantId);
  const uploadImage = useUploadMenuItemImage(restaurantId);
  const [draft, setDraft] = useState<Draft | null>(null);

  function edit(item: MenuItem) {
    setDraft({
      id: item.id,
      name: item.name,
      price: String(item.price),
      category: item.category ?? '',
    });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft) {
      return;
    }
    const current = menu.data?.find((item) => item.id === draft.id);
    const input = toInput(draft, current);
    if (!input) {
      return;
    }
    change.mutate(
      draft.id === null ? { kind: 'add', input } : { kind: 'update', menuItemId: draft.id, input },
      {
        onSuccess: () => {
          setDraft(null);
        },
      },
    );
  }

  return (
    <>
      <div className={styles.actions}>
        <Button
          variant="dark"
          onClick={() => {
            setDraft(emptyDraft);
          }}
        >
          Добавить блюдо
        </Button>
      </div>

      {draft && (
        <form className={styles.form} onSubmit={submit} aria-label="Блюдо">
          <h2 className={styles.formTitle}>
            {draft.id === null ? 'Новое блюдо' : 'Изменить блюдо'}
          </h2>
          <Field
            label="Название"
            value={draft.name}
            maxLength={255}
            onChange={(event) => {
              setDraft({ ...draft, name: event.target.value });
            }}
          />
          <Field
            label="Цена, ₽"
            inputMode="decimal"
            value={draft.price}
            onChange={(event) => {
              setDraft({ ...draft, price: event.target.value });
            }}
          />
          <Field
            label="Категория"
            value={draft.category}
            maxLength={100}
            onChange={(event) => {
              setDraft({ ...draft, category: event.target.value });
            }}
          />
          {change.isError && (
            <p className={styles.error} role="alert">
              {errorMessage(change.error)}
            </p>
          )}
          <div className={styles.formActions}>
            <Button type="submit" disabled={change.isPending}>
              Сохранить
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                setDraft(null);
              }}
            >
              Отмена
            </Button>
          </div>
        </form>
      )}

      {menu.isPending && (
        <div className={styles.skeleton} aria-busy="true" aria-label="Загружаем меню" />
      )}

      {menu.isError && (
        <StateMessage
          role="alert"
          title="Не получилось загрузить меню"
          text={errorMessage(menu.error)}
        >
          <Button
            variant="dark"
            onClick={() => {
              void menu.refetch();
            }}
          >
            Повторить
          </Button>
        </StateMessage>
      )}

      {uploadImage.isError && (
        <p className={styles.error} role="alert">
          {errorMessage(uploadImage.error)}
        </p>
      )}

      {menu.data && (
        <ul className={styles.list}>
          {menu.data.map((item) => (
            <li key={item.id} className={styles.row}>
              <div>
                <p className={styles.rowTitle}>{item.name}</p>
                <p className={styles.rowMeta}>
                  {formatPrice(item.price)}
                  {item.category ? ` · ${item.category}` : ''}
                  {!item.available && <span className={styles.stopped}> · в стоп-листе</span>}
                </p>
              </div>
              <div className={styles.actions}>
                <Button
                  variant="ghost"
                  size="small"
                  disabled={change.isPending}
                  onClick={() => {
                    change.mutate(
                      item.available
                        ? { kind: 'stop', menuItemId: item.id }
                        : { kind: 'resume', menuItemId: item.id },
                    );
                  }}
                >
                  {item.available ? 'В стоп-лист' : 'Вернуть в продажу'}
                </Button>
                <label className={styles.upload}>
                  {item.imageUrl ? 'Заменить фото' : 'Загрузить фото'}
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    className={styles.fileInput}
                    aria-label={`Фото блюда ${item.name}`}
                    disabled={uploadImage.isPending}
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      event.target.value = '';
                      if (file) {
                        uploadImage.mutate({ menuItemId: item.id, file });
                      }
                    }}
                  />
                </label>
                <Button
                  variant="ghost"
                  size="small"
                  onClick={() => {
                    edit(item);
                  }}
                >
                  Изменить
                </Button>
                <Button
                  variant="ghost"
                  size="small"
                  disabled={change.isPending}
                  onClick={() => {
                    change.mutate({ kind: 'remove', menuItemId: item.id });
                  }}
                >
                  Удалить
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
