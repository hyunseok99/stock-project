import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ThemeProvider, createTheme } from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import './styles/globals.css'
import App from './App'
import { WebSocketProvider } from './contexts/WebSocketContext'

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#2563eb', // text-blue-600
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <WebSocketProvider>
        <App />
      </WebSocketProvider>
    </ThemeProvider>
  </StrictMode>,
)
