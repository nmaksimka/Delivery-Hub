import {
  Bar,
  BarChart,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { Analytics } from '../api/analytics';
import { formatDay, formatMinutes } from '../lib/analyticsPeriod';
import { formatPrice, plural } from '../lib/format';
import styles from './AnalyticsDashboard.module.css';

const barColor = '#2f6f8f';
const lineColor = '#15171c';
const gridColor = '#e5e7ea';
const tickStyle = { fill: '#7a808b', fontSize: 12 };

const compactPrice = new Intl.NumberFormat('ru-RU', {
  notation: 'compact',
  maximumFractionDigits: 1,
});

function sum(values: number[]) {
  return values.reduce((total, value) => total + value, 0);
}

export function AnalyticsDashboard({ report }: { report: Analytics }) {
  const orders = sum(report.revenueByDay.map((day) => day.orders));
  const revenue = sum(report.revenueByDay.map((day) => day.revenue));
  const averageCheck = orders > 0 ? revenue / orders : 0;
  const revenueData = report.revenueByDay.map((day) => ({ ...day, label: formatDay(day.day) }));
  const timeData = report.deliveryTimeByDay.map((day) => ({
    label: formatDay(day.day ?? ''),
    average: day.averageMinutes,
    p90: day.p90Minutes,
  }));

  if (orders === 0) {
    return (
      <p className={styles.empty}>
        За выбранный период нет доставленных заказов, показывать пока нечего.
      </p>
    );
  }

  return (
    <div className={styles.dashboard}>
      <dl className={styles.totals}>
        <div className={styles.total}>
          <dt>Выручка</dt>
          <dd>{formatPrice(revenue)}</dd>
        </div>
        <div className={styles.total}>
          <dt>{plural(orders, 'Заказ', 'Заказа', 'Заказов')}</dt>
          <dd>{orders}</dd>
        </div>
        <div className={styles.total}>
          <dt>Средний чек</dt>
          <dd>{formatPrice(Math.round(averageCheck))}</dd>
        </div>
        <div className={styles.total}>
          <dt>Доставка, медиана</dt>
          <dd>{report.deliveryTime ? formatMinutes(report.deliveryTime.medianMinutes) : '—'}</dd>
        </div>
      </dl>

      <section className={styles.panel} aria-labelledby="chart-revenue">
        <h2 id="chart-revenue" className={styles.heading}>
          Выручка по дням
        </h2>
        <p className={styles.legend}>
          <span className={styles.swatchBar} /> за день
          <span className={styles.swatchLine} /> среднее за 7 дней
        </p>
        <div className={styles.chart}>
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={revenueData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid stroke={gridColor} vertical={false} />
              <XAxis dataKey="label" tick={tickStyle} tickLine={false} axisLine={false} />
              <YAxis
                tick={tickStyle}
                tickLine={false}
                axisLine={false}
                width={48}
                tickFormatter={(value: number) => compactPrice.format(value)}
              />
              <Tooltip formatter={(value) => formatPrice(Number(value))} />
              <Bar dataKey="revenue" name="Выручка" fill={barColor} radius={[4, 4, 0, 0]} />
              <Line
                dataKey="movingAverage"
                name="Среднее за 7 дней"
                stroke={lineColor}
                strokeWidth={2}
                dot={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      </section>

      <div className={styles.pair}>
        <section className={styles.panel} aria-labelledby="chart-dishes">
          <h2 id="chart-dishes" className={styles.heading}>
            Топ блюд
          </h2>
          <ol className={styles.dishes}>
            {report.topDishes.map((dish) => (
              <li key={dish.menuItemId}>
                <span className={styles.rank}>{dish.rank}</span>
                <span className={styles.dishName}>{dish.name}</span>
                <span className={styles.dishCount}>{dish.quantity} шт.</span>
                <span className={styles.dishRevenue}>{formatPrice(dish.revenue)}</span>
              </li>
            ))}
          </ol>
        </section>

        <section className={styles.panel} aria-labelledby="chart-time">
          <h2 id="chart-time" className={styles.heading}>
            Время доставки
          </h2>
          <p className={styles.legend}>
            <span className={styles.swatchBar} /> среднее
            <span className={styles.swatchLine} /> 90% заказов быстрее
          </p>
          <div className={styles.chartSmall}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={timeData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid stroke={gridColor} vertical={false} />
                <XAxis dataKey="label" tick={tickStyle} tickLine={false} axisLine={false} />
                <YAxis tick={tickStyle} tickLine={false} axisLine={false} width={32} />
                <Tooltip formatter={(value) => formatMinutes(Number(value))} />
                <Bar dataKey="average" name="Среднее" fill={barColor} radius={[4, 4, 0, 0]} />
                <Bar dataKey="p90" name="90-й процентиль" fill={lineColor} radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      </div>
    </div>
  );
}
