package mao.t2;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Project name(项目名称)：Netty_Redis协议通信
 * Package(包名): mao.t2
 * Class(类名): Client
 * Author(作者）: mao
 * Author QQ：1296193245
 * GitHub：https://github.com/maomao124/
 * Date(创建日期)： 2023/3/25
 * Time(创建时间)： 21:20
 * Version(版本): 1.0
 * Description(描述)： 无
 */

@Slf4j
public class Client
{
    @SneakyThrows
    public static void main(String[] args)
    {
        Scanner input = new Scanner(System.in);
        while (true)
        {
            String requestBody = input.nextLine();
            if ("q".equals(requestBody))
            {
                return;
            }
            log.debug("请求体：" + requestBody);
            String resp = httpGet("http://localhost:8080/index.html", requestBody);
            log.info("响应内容：" + resp);
        }
    }

    /**
     * http get请求
     *
     * @param urlString   url字符串
     * @param requestBody 请求体
     * @return {@link String}
     */
    private static String httpGet(String urlString, String requestBody) throws IOException
    {
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try
        {
            URL url = new URL(urlString);
            URLConnection urlConnection = url.openConnection();
            urlConnection.setDoOutput(true);
            //连接
            urlConnection.connect();
            outputStream = urlConnection.getOutputStream();
            //写请求体
            outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
            inputStream = urlConnection.getInputStream();
            inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder stringBuilder = new StringBuilder();
            String resp;
            while ((resp = bufferedReader.readLine()) != null)
            {
                stringBuilder.append(resp);
            }
            return stringBuilder.toString();
        }
        finally
        {
            if (bufferedReader != null)
            {
                bufferedReader.close();
            }
            if (inputStreamReader != null)
            {
                inputStreamReader.close();
            }
            if (inputStream != null)
            {
                inputStream.close();
            }
            if (outputStream != null)
            {
                outputStream.close();
            }
        }
    }
}
