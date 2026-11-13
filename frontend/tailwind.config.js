/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        navy: {
          50: '#f0f4f8',
          100: '#d9e2ec',
          200: '#bcccdc',
          300: '#9fb3c8',
          400: '#829ab1',
          500: '#627d98',
          600: '#486581',
          700: '#334e68',
          800: '#1e3a5f',
          900: '#0f2942',
          950: '#0b1d30',
        },
        brand: {
          50: '#eef6ff',
          100: '#d9ecff',
          500: '#1a56db',
          600: '#1e40af',
          700: '#1d3557',
          800: '#132840',
        }
      }
    },
  },
  plugins: [],
}

