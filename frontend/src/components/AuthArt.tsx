import styles from '../pages/AuthPage.module.css';

export function AuthArt() {
  return (
    <div className={styles.art} aria-hidden="true">
      <span className={styles.artLime} />
      <span className={styles.artTomato} />
      <span className={styles.artInk} />
      <span className={styles.artSun} />
      <span className={styles.artMint} />
    </div>
  );
}
