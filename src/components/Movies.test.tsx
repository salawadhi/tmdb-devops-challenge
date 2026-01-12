// src/components/Movies.test.tsx
import React from 'react';
import { render, screen } from '@testing-library/react';
import Movies from './Movies';

test('renders TMDB Code Challenge heading', () => {
  render(<Movies />);
  const headingElement = screen.getByText(/TMDB Code Challenge/i);
  expect(headingElement).toBeInTheDocument();
});