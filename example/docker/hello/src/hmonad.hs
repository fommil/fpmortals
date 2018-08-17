{-# LANGUAGE OverloadedStrings #-}

import Web.Scotty

import Data.Monoid (mconcat)
import Data.Aeson.Types

main = scotty 80 $ do
  get "/:word" $ do
    beam <- param "word"
    json $  object ["greetings" .= String beam]
