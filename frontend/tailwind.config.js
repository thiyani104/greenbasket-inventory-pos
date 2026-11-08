/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#effaf1', 100: '#d7f2dc', 200: '#b1e4bb', 300: '#80cf93', 400: '#4db46a',
          500: '#2e9a4f', 600: '#1f7c3d', 700: '#1a6334', 800: '#174e2c', 900: '#144126',
        },
      },
      fontFamily: { sans: ['Inter', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'Roboto', 'sans-serif'] },
    },
  },
  plugins: [],
};
