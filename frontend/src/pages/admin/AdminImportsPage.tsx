import { errorMessage } from '../../api/http';
import { useImportJobs, useStartImport } from '../../api/queries';
import { Button } from '../../components/Button';
import { StateMessage } from '../../components/StateMessage';
import { formatDateTime } from '../../lib/format';
import styles from '../Management.module.css';

const sourceLabels: Record<string, string> = {
  MEALDB: 'Блюда (TheMealDB)',
  OPENFOODFACTS: 'Продукты (Open Food Facts)',
};

const statusLabels: Record<string, string> = {
  PENDING: 'В очереди',
  RUNNING: 'Выполняется',
  COMPLETED: 'Готово',
  FAILED: 'Ошибка',
};

export function AdminImportsPage() {
  const jobs = useImportJobs();
  const start = useStartImport();

  return (
    <>
      <div className={styles.actions}>
        <Button
          variant="dark"
          disabled={start.isPending}
          onClick={() => {
            start.mutate('meals');
          }}
        >
          Импортировать блюда
        </Button>
        <Button
          variant="dark"
          disabled={start.isPending}
          onClick={() => {
            start.mutate('products');
          }}
        >
          Импортировать продукты
        </Button>
      </div>
      {start.isError && (
        <p className={styles.error} role="alert">
          {errorMessage(start.error, 'Не получилось запустить импорт')}
        </p>
      )}

      {jobs.isPending && (
        <div className={styles.skeleton} aria-busy="true" aria-label="Загружаем импорты" />
      )}

      {jobs.isError && (
        <StateMessage
          role="alert"
          title="Не получилось загрузить импорты"
          text={errorMessage(jobs.error)}
        >
          <Button
            variant="dark"
            onClick={() => {
              void jobs.refetch();
            }}
          >
            Повторить
          </Button>
        </StateMessage>
      )}

      {jobs.data?.length === 0 && (
        <StateMessage title="Импортов ещё не было" text="Запустите первый кнопкой выше." />
      )}

      {jobs.data && jobs.data.length > 0 && (
        <ul className={styles.list}>
          {jobs.data.map((job) => (
            <li key={job.id} className={styles.row}>
              <div>
                <p className={styles.rowTitle}>{sourceLabels[job.source] ?? job.source}</p>
                <p className={styles.rowMeta}>
                  {formatDateTime(job.createdAt)} · создано блюд {job.itemsCreated}, обновлено{' '}
                  {job.itemsUpdated}, ресторанов {job.restaurantsCreated}
                </p>
                {job.error && <p className={styles.error}>{job.error}</p>}
                <div
                  className={styles.progress}
                  role="progressbar"
                  aria-valuenow={job.progressPercent}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-label="Ход импорта"
                >
                  <div
                    className={styles.progressBar}
                    style={{ width: `${job.progressPercent}%` }}
                  />
                </div>
              </div>
              <span>{statusLabels[job.status] ?? job.status}</span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
