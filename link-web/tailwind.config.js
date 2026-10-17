/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#409EFF',
          dark: '#337ECC',
          light: '#ECF5FF',
        },
        sidebar: '#304156',
        page: '#F5F7FA',
      },
    },
  },
  plugins: [],
}
